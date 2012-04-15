package net.floodlightcontroller.storage.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import net.floodlightcontroller.storage.IPredicate;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.StorageSourceNotification;

public class SqliteStorageSource extends SQLStorageSource {

	/**
	 * we may want to move the information in this method to another file
	 * (possibly password protected) and only leave the implementation here
	 * 
	 * @return
	 */
	@Override
	public Connection openConnection() {
		Connection conn = null;
		try {
			String driverName = "org.sqlite.JDBC";
			Class.forName(driverName);

			// String serverName = "localhost";
			String mydb = "target/bin/floodlight.db";
			String url = "jdbc:sqlite:" + mydb; // provide jdbc with path to db
												// file
			conn = DriverManager.getConnection(url);
		} catch (ClassNotFoundException e) {
			System.out.println("could not find database driver");
		} catch (SQLException e) {
			System.out.println("could not connect to database\n"
					+ e.getMessage());
			e.printStackTrace();
			
		}
		return conn;
	}
	
	@Override
	public void setTablePrimaryKeyName(String tableName, String primaryKeyName){
		
		try {
			Connection conn = openConnection();
			Statement stmt = conn.createStatement();
			Set<String> columns = getColumns(tableName);
			//System.out.println(columns.toString());
			if (!columns.isEmpty()){
				
				StringBuffer sb = new StringBuffer();
				sb.append("DROP TABLE if exists temp;");
				//System.out.println(sb.toString());
				stmt.execute(sb.toString());
				stmt.clearBatch();
				
				sb = new StringBuffer();
				sb.append("CREATE TABLE temp(");
				for (String c : columns){
					sb.append(c +",");
				}
				sb.deleteCharAt(sb.length()-1);
				sb.append(");");
				//System.out.println(sb.toString());
				stmt.execute(sb.toString());
				stmt.clearBatch();
			
				sb = new StringBuffer();
				sb.append("INSERT INTO temp SELECT * FROM " + tableName + ";");
				//System.out.println(sb.toString());
				stmt.execute(sb.toString());
				stmt.clearBatch();
				
				sb = new StringBuffer();
				sb.append("DROP TABLE " + tableName + ";");
				//System.out.println(sb.toString());
				stmt.execute(sb.toString());
				stmt.clearBatch();
				
				sb = new StringBuffer();
				sb.append("CREATE TABLE " + tableName + "(");
				for (String c : columns){
					sb.append(c +" VARCHAR(100), ");
				}
				sb.append("PRIMARY KEY(" + primaryKeyName + "));");
				//System.out.println("\t" + sb.toString());
				stmt.execute(sb.toString());
				stmt.clearBatch();
				
				sb = new StringBuffer();
				sb.append("INSERT INTO " + tableName + " SELECT * FROM temp;");
				//System.out.println(sb.toString());
				stmt.execute(sb.toString());
				
				
				sb = new StringBuffer();
				sb.append("DROP TABLE if exists temp;");
				//System.out.println(sb.toString());
				stmt.execute(sb.toString());
				stmt.clearBatch();
			}else{
				columns.add(primaryKeyName);
				//System.out.println("\t\ttable empty, creating a new table");
				dropTable(tableName);
				createTable(tableName, columns, primaryKeyName);
			}
			
		}catch(SQLException e){
			System.out.println("Could not set primary key\n" + e.getMessage());
		}
		
		
	}

	@Override
	public String getTablePrimaryKeyName(String tableName) {
		String pKey = "";
		try {
			Connection conn = openConnection();
			Statement stmt = conn.createStatement();

			String sql = "pragma table_info(" + tableName + ");";
			//System.out.println(sql);
			ResultSet sqlresult = stmt.executeQuery(sql);

			while (pKey == "" && sqlresult.next()) {
				if (sqlresult.getInt(6) == 1) {
					pKey = sqlresult.getString(2);
				}

			}

			conn.close();
		} catch (SQLException e) {
			System.out.println("failed to get primary key\n" + e.getMessage());
		}
		return pKey;
	}

	@Override
	public Map<String, Object> formatValues(Map<String, Object> values) {
		Iterator<String> it = values.keySet().iterator();
		Object value = null;
		Map<String, Object> newValues = new HashMap<String, Object>();
		for (int i = 0; i < values.size(); i++) {
			String key = it.next();
			value = values.get(key);
			value = formatValue(value);
			newValues.put(key, value);
		}
		return newValues;
	}

	@Override
	public Object formatValue(Object value) {
		try {
			if (value.toString().equals("true"))
				value = 1;
			else if (value.toString().equals("false"))
				value = 0;
			else if (value.getClass() == java.util.Date.class){
				value = (Date) value;
				SimpleDateFormat dateFormat = new SimpleDateFormat(
					"yyyy-MM-dd HH:mm:ss.SSS");
				dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
				value = "strftime('%Y-%m-%d %H:%M:%f', '" + dateFormat.format(value) + "')";
			}
			else{
				if (!value.toString().equals("null")
					&& !value.toString().equals("NULL")) {
					if (!value.toString().contains("'"))
						value = "'" + value.toString() + "'";
				}
			}
		} catch (NullPointerException e) {
			//System.out.println("SQL formatting: value was null");
			return "null";
		}
		return value;
	}
	
	@Override
	public Set<String> getColumns(String tableName){
		StringBuffer sb = new StringBuffer();
		Set<String> columns = new HashSet<String>();
		try {
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();

			sb.append("pragma table_info(" + tableName + ");");
			ResultSet rs = stmt.executeQuery(sb.toString());
			while(rs.next()){
				//System.out.println(rs.getString(2));
				columns.add(rs.getString(2));
			}
		}catch(SQLException e){
			if (!e.getMessage().contains("query does not return"))
				System.out.println("Failed to get columns\n" + e.getMessage());
		}
		return columns;
	}

}
