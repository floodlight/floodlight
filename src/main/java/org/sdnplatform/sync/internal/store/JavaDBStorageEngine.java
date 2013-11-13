package org.sdnplatform.sync.internal.store;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import javax.sql.ConnectionPoolDataSource;
import javax.xml.bind.DatatypeConverter;

import net.floodlightcontroller.core.annotations.LogMessageCategory;

import org.apache.derby.jdbc.EmbeddedConnectionPoolDataSource40;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.IVersion.Occurred;
import org.sdnplatform.sync.error.ObsoleteVersionException;
import org.sdnplatform.sync.error.PersistException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.SyncRuntimeException;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.sdnplatform.sync.internal.util.EmptyClosableIterator;
import org.sdnplatform.sync.internal.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

/**
 * Persistent storage engine that keeps its data in a JDB database
 * @author readams
 */
@LogMessageCategory("State Synchronization")
public class JavaDBStorageEngine implements IStorageEngine<ByteArray, byte[]> {
    protected static final Logger logger =
            LoggerFactory.getLogger(JavaDBStorageEngine.class.getName());
    
    private static String CREATE_DATA_TABLE = 
            " (datakey varchar(4096) primary key," +
            "datavalue blob)";
    private static String SELECT_ALL =
            "select * from <tbl>";
    private static String SELECT_KEY =
            "select * from <tbl> where datakey = ?";
    private static String INSERT_KEY =
            "insert into <tbl> values (?, ?)";
    private static String UPDATE_KEY =
            "update <tbl> set datavalue = ? where datakey = ?";
    private static String DELETE_KEY =
            "delete from <tbl> where datakey = ?";
    private static String TRUNCATE =
            "delete from <tbl>";
    
    private String name;
    private String dbTableName;
    
    private ConnectionPoolDataSource dataSource;

    /**
     * Interval in milliseconds before tombstones will be cleared.
     */
    private int tombstoneDeletion = 24 * 60 * 60 * 1000;

    private static final ObjectMapper mapper = 
            new ObjectMapper(new SmileFactory());
    {
        System.setProperty("derby.stream.error.method",
                           DerbySlf4jBridge.getBridgeMethod());
    }

    /**
     * Construct a new storage engine that will use the provided engine
     * as a delegate and provide persistence for its data.  Note that
     * the delegate engine must be empty when this object is constructed
     * @param delegate the delegate engine to persist
     * @throws SyncException 
     */
    public JavaDBStorageEngine(String name, 
                               ConnectionPoolDataSource dataSource)
            throws PersistException {
        super();
        
        this.name = name;
        this.dbTableName = name.replace('.', '_');
        this.dataSource = dataSource;

        try {
            initTable();
        } catch (SQLException sqle) {
            throw new PersistException("Could not initialize persistent storage",
                                       sqle);
        }
    }
    
    // *******************************
    // StorageEngine<ByteArray,byte[]>
    // *******************************

    @Override
    public List<Versioned<byte[]>> get(ByteArray key) throws SyncException {
        StoreUtils.assertValidKey(key);
        Connection dbConnection = null;
        PreparedStatement stmt = null;
        try {
            dbConnection = getConnection();
            stmt = dbConnection.prepareStatement(getSql(SELECT_KEY));
            return doSelect(stmt, getKeyAsString(key));

        } catch (Exception e) {
            throw new PersistException("Could not retrieve key" +
                    " from database",
                    e);
        } finally {
            cleanupSQL(dbConnection, stmt);
        }
    }

    @Override
    public IClosableIterator<Entry<ByteArray, List<Versioned<byte[]>>>>
            entries() {
        PreparedStatement stmt = null;
        Connection dbConnection = null;
        try {
            // we never close this connection unless there's an error; 
            // it must be closed by the DbIterator
            dbConnection = getConnection();
            stmt = dbConnection.prepareStatement(getSql(SELECT_ALL));
            ResultSet rs = stmt.executeQuery();
            return new DbIterator(dbConnection, stmt, rs);                
        } catch (Exception e) {
            logger.error("Could not create iterator on data", e);
            try {
                cleanupSQL(dbConnection, stmt);
            } catch (Exception e2) {
                logger.error("Failed to clean up after error", e2);
            }
            return new EmptyClosableIterator<Entry<ByteArray,List<Versioned<byte[]>>>>();
        }
    }

    @Override
    public void put(ByteArray key, Versioned<byte[]> value) 
            throws SyncException {
        StoreUtils.assertValidKey(key);
        Connection dbConnection = null;
        try {
            PreparedStatement stmt = null;
            PreparedStatement update = null;
            try {
                String keyStr = getKeyAsString(key);
                dbConnection = getConnection();
                dbConnection.setAutoCommit(false);
                stmt = dbConnection.prepareStatement(getSql(SELECT_KEY));
                List<Versioned<byte[]>> values = doSelect(stmt, keyStr);

                int vindex;
                if (values.size() > 0) {
                    update = dbConnection.prepareStatement(getSql(UPDATE_KEY));
                    update.setString(2, keyStr);
                    vindex = 1;
                } else {
                    update = dbConnection.prepareStatement(getSql(INSERT_KEY));
                    update.setString(1, keyStr);
                    vindex = 2;
                }

                List<Versioned<byte[]>> itemsToRemove = 
                        new ArrayList<Versioned<byte[]>>(values.size());
                for(Versioned<byte[]> versioned: values) {
                    Occurred occurred = value.getVersion().compare(versioned.getVersion());
                    if(occurred == Occurred.BEFORE) {
                        throw new ObsoleteVersionException("Obsolete version for key '" + key
                                                           + "': " + value.getVersion());
                    } else if(occurred == Occurred.AFTER) {
                        itemsToRemove.add(versioned);
                    }
                }
                values.removeAll(itemsToRemove);
                values.add(value);

                ByteArrayInputStream is = 
                        new ByteArrayInputStream(mapper.writeValueAsBytes(values));                
                update.setBinaryStream(vindex, is);
                update.execute();
                dbConnection.commit();
            } catch (SyncException e) {
                dbConnection.rollback();
                throw e;
            } catch (Exception e) {
                dbConnection.rollback();
                throw new PersistException("Could not retrieve key from database",
                                           e);
            } finally {
                cleanupSQL(dbConnection, stmt, update);
            }
        } catch (SQLException e) {
            cleanupSQL(dbConnection);
            throw new PersistException("Could not clean up", e);
        }
    }

    @Override
    public IClosableIterator<ByteArray> keys() {
        return StoreUtils.keys(entries());
    }

    @Override
    public void truncate() throws SyncException {
        Connection dbConnection = null;
        PreparedStatement update = null;
        try {
            dbConnection = getConnection();
            update = dbConnection.prepareStatement(getSql(TRUNCATE));
            update.execute();
        } catch (Exception e) {
            logger.error("Failed to truncate store " + getName(), e);
        } finally {
            cleanupSQL(dbConnection, update);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void close() throws SyncException {
        
    }

    @Override
    public boolean writeSyncValue(ByteArray key,
                                  Iterable<Versioned<byte[]>> values) {
        boolean success = false;
        for (Versioned<byte[]> value : values) {
            try {
                put (key, value);
                success = true;
            } catch (PersistException e) {
                logger.error("Failed to sync value because of " +
                             "persistence exception", e);
            } catch (SyncException e) {
                // ignore obsolete version exception
            }
        }
        return success;
    }

    @Override
    public List<IVersion> getVersions(ByteArray key) throws SyncException {
        return StoreUtils.getVersions(get(key));
    }

    @Override
    public void cleanupTask() throws SyncException {
        Connection dbConnection = null;
        PreparedStatement stmt = null;
        try {
            dbConnection = getConnection();
            dbConnection.setAutoCommit(true);
            stmt = dbConnection.prepareStatement(getSql(SELECT_ALL));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                List<Versioned<byte[]>> items = getVersionedList(rs);
                if (StoreUtils.canDelete(items, tombstoneDeletion)) {
                    doClearTombstone(rs.getString("datakey"));
                }
            }                
        } catch (Exception e) {
            logger.error("Failed to delete key", e);
        } finally {
            cleanupSQL(dbConnection, stmt);
        }
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void setTombstoneInterval(int interval) {
        this.tombstoneDeletion = interval;
    }

    // *******************
    // JavaDBStorageEngine
    // *******************

    /**
     * Get a connection pool data source for use by Java DB storage engines
     * @param dbPath The path where the db will be located
     * @param memory whether to actually use a memory database
     * @return the {@link ConnectionPoolDataSource}
     */
    public static ConnectionPoolDataSource getDataSource(String dbPath, 
                                                         boolean memory) {

        EmbeddedConnectionPoolDataSource40 ds = 
                new EmbeddedConnectionPoolDataSource40();
        if (memory) {
            ds.setDatabaseName("memory:SyncDB");                
        } else {
            String path = "SyncDB";
            if (dbPath != null) {
                File f = new File(dbPath);
                f = new File(dbPath,"SyncDB");
                path = f.getAbsolutePath();
            }            

            ds.setDatabaseName(path);
        }
        ds.setCreateDatabase("create");
        ds.setUser("floodlight");
        ds.setPassword("floodlight");
        return ds;
    }
    
    // *************
    // Local methods
    // *************
    
    private static void cleanupSQL(Connection dbConnection) 
            throws SyncException {
        cleanupSQL(dbConnection, (PreparedStatement[])null);
    }
    
    private static void cleanupSQL(Connection dbConnection, 
                                   PreparedStatement... stmts) 
                                    throws SyncException {
        try {
            if (stmts != null) {
                for (PreparedStatement stmt : stmts) {
                    if (stmt != null) 
                        stmt.close();
                }
            }
        } catch (SQLException e) {
            throw new PersistException("Could not close statement", e);
        } finally {
            try {
                if (dbConnection != null && !dbConnection.isClosed())
                    dbConnection.close();
            } catch (SQLException e) {
                throw new PersistException("Could not close connection", e);
            }
        }
    }
    
    private Connection getConnection() throws SQLException {
        Connection conn = dataSource.getPooledConnection().getConnection();
        conn.setTransactionIsolation(Connection.
                                     TRANSACTION_READ_COMMITTED);
        return conn;
    }
    
    private void initTable() throws SQLException {
        Connection dbConnection = getConnection();
        Statement statement = null;
        statement = dbConnection.createStatement();
        try {
            statement.execute("CREATE TABLE " + dbTableName +
                              CREATE_DATA_TABLE);
        } catch (SQLException e) {
            // eat table already exists exception
            if (!"X0Y32".equals(e.getSQLState()))
                throw e;
        } finally {
            if (statement != null) statement.close();
            dbConnection.close();
        }
    }
    
    private String getKeyAsString(ByteArray key) 
            throws UnsupportedEncodingException {
        return DatatypeConverter.printBase64Binary(key.get());
    }

    private static ByteArray getStringAsKey(String keyStr) 
            throws UnsupportedEncodingException {
        return new ByteArray(DatatypeConverter.parseBase64Binary(keyStr));
    }
    
    private String getSql(String sql) {
        return sql.replace("<tbl>", dbTableName);
    }
    
    private static List<Versioned<byte[]>> getVersionedList(ResultSet rs) 
                throws SQLException, JsonParseException, 
                    JsonMappingException, IOException {
        InputStream is = rs.getBinaryStream("datavalue");
        return mapper.readValue(is,
                                new TypeReference<List<VCVersioned<byte[]>>>() {});
    }
    
    private List<Versioned<byte[]>> doSelect(PreparedStatement stmt,
                                             String key) 
                throws SQLException, JsonParseException, 
                    JsonMappingException, IOException {
        stmt.setString(1, key);
        ResultSet rs = stmt.executeQuery();
        
        if (rs.next()) {
            return getVersionedList(rs);
        } else {
            return new ArrayList<Versioned<byte[]>>(0);
        }
    }

    private void doClearTombstone(String keyStr) throws SyncException {
        Connection dbConnection = null;
        try {
            PreparedStatement stmt = null;
            PreparedStatement update = null;
            try {
                dbConnection = getConnection();
                dbConnection.setAutoCommit(false);    
                stmt = dbConnection.prepareStatement(getSql(SELECT_KEY));
                List<Versioned<byte[]>> items = doSelect(stmt, keyStr);
                if (StoreUtils.canDelete(items, tombstoneDeletion)) {
                    update = dbConnection.prepareStatement(getSql(DELETE_KEY));
                    update.setString(1, keyStr);
                    update.execute();
                }
                dbConnection.commit();

            } catch (Exception e) {
                if (dbConnection != null)
                    dbConnection.rollback();
                logger.error("Failed to delete key", e);
            } finally {
                cleanupSQL(dbConnection, stmt, update);
            }
        } catch (SQLException e) {
            logger.error("Failed to clean up after error", e);
            cleanupSQL(dbConnection);
        }
    }
    
    private static class DbIterator implements 
        IClosableIterator<Entry<ByteArray,List<Versioned<byte[]>>>> {

        private final Connection dbConnection;
        private final PreparedStatement stmt;
        private final ResultSet rs;
        private boolean hasNext = false;
        private boolean hasNextSet = false;
        
        public DbIterator(Connection dbConnection,
                          PreparedStatement stmt, 
                          ResultSet rs) {
            super();
            this.dbConnection = dbConnection;
            this.stmt = stmt;
            this.rs = rs;
        }

        @Override
        public boolean hasNext() {
            try {
                if (hasNextSet) return hasNext;
                hasNextSet = true;
                hasNext = rs.next();
            } catch (Exception e) {
                logger.error("Error in DB Iterator", e);
                hasNextSet = true;
                hasNext = false;
            }
            return hasNext;
        }

        @Override
        public Pair<ByteArray, List<Versioned<byte[]>>> next() {
            if (hasNext()) {
                try {
                    ByteArray key = getStringAsKey(rs.getString("datakey"));
                    List<Versioned<byte[]>> vlist = getVersionedList(rs);
                    hasNextSet = false;
                    return new Pair<ByteArray, 
                                    List<Versioned<byte[]>>>(key, vlist);
                } catch (Exception e) {
                    throw new SyncRuntimeException("Error in DB Iterator", 
                                                   new PersistException(e));
                }
            } else {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            try {
                cleanupSQL(dbConnection, stmt);
            } catch (SyncException e) {
                logger.error("Could not close DB iterator", e);
            }
        }
        
    }
}
