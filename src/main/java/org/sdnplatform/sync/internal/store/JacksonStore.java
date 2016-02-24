package org.sdnplatform.sync.internal.store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.core.type.TypeReference;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IVersion;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.error.SerializationException;
import org.sdnplatform.sync.error.SyncException;
import org.sdnplatform.sync.error.SyncRuntimeException;
import org.sdnplatform.sync.internal.util.ByteArray;
import org.sdnplatform.sync.internal.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A store that will serialize and deserialize objects to JSON using Jackson
 */
public class JacksonStore<K, V> implements IStore<K, V> {
    protected static Logger logger =
            LoggerFactory.getLogger(JacksonStore.class);

    protected static final ObjectMapper mapper = 
            new ObjectMapper(new SmileFactory());
    static {
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,
                         true);
    }
    
    private final IStore<ByteArray, byte[]> delegate;
    
    private final ObjectWriter keyWriter;
    private final ObjectWriter valueWriter;
    private final ObjectReader keyReader;
    private final ObjectReader valueReader;
    
    private final boolean keyAsTree;
    private final boolean valueAsTree;

    public JacksonStore(IStore<ByteArray, byte[]> delegate,
                        Class<K> keyClass,
                        Class<V> valueClass) {
        super();
        this.delegate = delegate;
        if (keyClass.isAssignableFrom(JsonNode.class)) {
            keyAsTree = true;
            this.keyWriter = null;
            this.keyReader = null;
        } else {
            keyAsTree = false;
            this.keyWriter = mapper.writerWithType(keyClass);
            this.keyReader = mapper.reader(keyClass);
        }
        if (valueClass.isAssignableFrom(JsonNode.class)) {
            valueAsTree = true;
            this.valueWriter = null;
            this.valueReader = null;
        } else {
            valueAsTree = false;
            this.valueWriter = mapper.writerWithType(valueClass);
            this.valueReader = mapper.reader(valueClass);
        }
    }
    
    public JacksonStore(IStore<ByteArray, byte[]> delegate,
                        TypeReference<K> keyType,
                        TypeReference<V> valueType) {
        super();
        this.delegate = delegate;
        keyAsTree = false;
        valueAsTree = false;
        this.keyWriter = mapper.writerWithType(keyType);
        this.keyReader = mapper.reader(keyType);
        this.valueWriter = mapper.writerWithType(valueType);
        this.valueReader = mapper.reader(valueType);
    }

    // ************
    // Store<K,V,T>
    // ************
    @Override
    public List<Versioned<V>> get(K key) throws SyncException {
        ByteArray keybytes = getKeyBytes(key);
        List<Versioned<byte[]>> values = delegate.get(keybytes);
        return convertValues(values);
    }

    @Override
    public IClosableIterator<Entry<K, List<Versioned<V>>>> entries() {
        return new JacksonIterator(delegate.entries());
    }

    @Override
    public void put(K key, Versioned<V> value)
            throws SyncException {
        ByteArray keybytes = getKeyBytes(key);
        byte[] valuebytes = value.getValue() != null 
                ? getValueBytes(value.getValue()) 
                : null;
        delegate.put(keybytes,
                     new Versioned<byte[]>(valuebytes, value.getVersion()));
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void close() throws SyncException {
        delegate.close();
    }

    @Override
    public List<IVersion> getVersions(K key) throws SyncException {
        ByteArray keybytes = getKeyBytes(key);
        return delegate.getVersions(keybytes);
    }

    // *************
    // Local methods
    // *************

    private ByteArray getKeyBytes(K key)
            throws SyncException {
        if (key == null)
            throw new IllegalArgumentException("Cannot get null key");

        try {
            ByteArray k = null;
            if (keyAsTree)
                k = new ByteArray(mapper.writeValueAsBytes(key));
            else
                k = new ByteArray(keyWriter.writeValueAsBytes(key));
            
            if (logger.isTraceEnabled()) {
                logger.trace("Converted key {} to {}", key, k);
            }
            return k;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    private byte[] getValueBytes(V value) throws SyncException {
        try {
            byte[] v = null;
            if (valueAsTree)
                v = mapper.writeValueAsBytes(value);
            else
                v = valueWriter.writeValueAsBytes(value);
            
            if (logger.isTraceEnabled()) {
                logger.trace("Converted value {} to {}", 
                             value, Arrays.toString(v));
            }
            return v;
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private V getValueObject(byte[] value) throws SyncException {
        try {
            if (value == null) return null;
            if (valueAsTree)
                return (V)mapper.readTree(value);
            else
                return valueReader.readValue(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private K getKeyObject(ByteArray key) throws SyncException {
        try {
            if (keyAsTree)
                return (K)mapper.readTree(key.get());
            else
                return keyReader.readValue(key.get());
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }
    
    private List<Versioned<V>> convertValues(List<Versioned<byte[]>> values) 
            throws SyncException {
        if (values != null) {
            List<Versioned<V>> objectvalues =
                new ArrayList<Versioned<V>>(values.size());
            for (Versioned<byte[]> vb : values) {
                objectvalues.add(new Versioned<V>(getValueObject(vb.getValue()),
                        vb.getVersion()));
            }
            return objectvalues;        
        }
        return null;
    }
    
    private class JacksonIterator implements 
        IClosableIterator<Entry<K, List<Versioned<V>>>> {

        IClosableIterator<Entry<ByteArray, List<Versioned<byte[]>>>> delegate;
        
        public JacksonIterator(IClosableIterator<Entry<ByteArray, 
                               List<Versioned<byte[]>>>> delegate) {
            super();
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Entry<K, List<Versioned<V>>> next() {
            Entry<ByteArray, List<Versioned<byte[]>>> n = delegate.next();
            try {
                return new Pair<K, List<Versioned<V>>>(getKeyObject(n.getKey()), 
                                        convertValues(n.getValue()));
            } catch (SyncException e) {
                throw new SyncRuntimeException("Failed to construct next value",
                                               e);
            }
        }

        @Override
        public void remove() {
            delegate.remove();
        }

        @Override
        public void close() {
            delegate.close();
        }
        
    }
}
