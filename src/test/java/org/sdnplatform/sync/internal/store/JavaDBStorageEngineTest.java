package org.sdnplatform.sync.internal.store;

import java.util.ArrayList;
import java.util.List;

import javax.sql.ConnectionPoolDataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.sync.internal.TUtils;
import org.sdnplatform.sync.internal.store.IStorageEngine;
import org.sdnplatform.sync.internal.store.JavaDBStorageEngine;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.sdnplatform.sync.internal.version.VectorClock;

import static org.junit.Assert.*;
import static org.sdnplatform.sync.internal.TUtils.*;


public class JavaDBStorageEngineTest extends AbstractStorageEngineT {

    private IStorageEngine<ByteArray, byte[]> store;

    @Before
    public void setUp() throws Exception {
        ConnectionPoolDataSource dataSource = 
                JavaDBStorageEngine.getDataSource(null, true);
        this.store = new JavaDBStorageEngine("test", dataSource); 
    }
    
    @After
    public void tearDown() throws Exception {
        this.store.truncate();
        this.store.close();
        this.store = null;
    }
    
    @Override
    public IStorageEngine<ByteArray, byte[]> getStorageEngine() {
        return store;
    }

    @Override
    public List<ByteArray> getKeys(int numKeys) {
        List<ByteArray> keys = new ArrayList<ByteArray>(numKeys);
        for(int i = 0; i < numKeys; i++)
            keys.add(new ByteArray(TUtils.randomBytes(10)));
        return keys;
    }

    @Test
    public void testSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        VectorClock clock = getClock(1,2);
        String cs = mapper.writeValueAsString(clock);
        VectorClock reconstructed = 
                mapper.readValue(cs, new TypeReference<VectorClock>() {});
        assertEquals(clock, reconstructed);
    }
    
}
