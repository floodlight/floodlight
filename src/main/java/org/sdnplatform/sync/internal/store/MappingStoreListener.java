package org.sdnplatform.sync.internal.store;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.sdnplatform.sync.IStoreListener;
import org.sdnplatform.sync.IStoreListener.UpdateType;
import org.sdnplatform.sync.internal.util.ByteArray;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * A class that will map from the raw serialized keys to the appropriate key
 * type for a store listener
 * @author readams
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class MappingStoreListener {
    TypeReference typeRef;
    Class keyClass;    
    IStoreListener listener;

    public MappingStoreListener(TypeReference typeRef, Class keyClass,
                                IStoreListener listener) {
        super();
        this.typeRef = typeRef;
        this.keyClass = keyClass;
        this.listener = listener;
    }

    public void notify(Iterator<ByteArray> keys, UpdateType type) {
        listener.keysModified(new MappingIterator(keys), type);
    }
    
    class MappingIterator implements Iterator {
        Iterator<ByteArray> keys;
        protected Object next;

        public MappingIterator(Iterator<ByteArray> keys) {
            super();
            this.keys = keys;
        }

        private Object map() {
            try {
                ByteArray ka = keys.next();
                Object key = null;
                if (typeRef != null)
                    key = JacksonStore.mapper.readValue(ka.get(), typeRef);
                else if (keyClass != null)
                    key = JacksonStore.mapper.readValue(ka.get(), keyClass);

                return key;
            } catch (Exception e) {
                return null;
            } 
        }
        
        @Override
        public boolean hasNext() {
            if (next != null) return true;
            while (keys.hasNext()) {
                next = map();
                if (next != null) return true;
            }
            return false;
        }

        @Override
        public Object next() {
            if (hasNext()) {
                Object cur = next;
                next = null;
                return cur;
            }
            throw new NoSuchElementException();            
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }
}
