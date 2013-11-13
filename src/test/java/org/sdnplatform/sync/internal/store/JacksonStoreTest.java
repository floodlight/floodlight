package org.sdnplatform.sync.internal.store;

import java.util.ArrayList;
import java.util.List;
import org.sdnplatform.sync.internal.store.IStore;
import org.sdnplatform.sync.internal.store.InMemoryStorageEngine;
import org.sdnplatform.sync.internal.store.JacksonStore;
import org.sdnplatform.sync.internal.util.ByteArray;


public class JacksonStoreTest extends AbstractStoreT<Key, TBean> {

    @Override
    public IStore<Key, TBean> getStore() throws Exception {
        IStore<ByteArray,byte[]> ims =
                new InMemoryStorageEngine<ByteArray,byte[]>("test");
        IStore<Key,TBean> js =
                new JacksonStore<Key, TBean>(ims, Key.class, TBean.class);
        return js;
    }

    @Override
    public List<TBean> getValues(int numValues) {
        List<TBean> v = new ArrayList<TBean>(numValues);
        for (int i = 0; i < numValues; i++) {
            TBean tb = new TBean();
            tb.setI(i);
            tb.setS("" + i);
            v.add(tb);
        }
        return v;
    }

    @Override
    public List<Key> getKeys(int numKeys) {
        List<Key> k = new ArrayList<Key>(numKeys);
        for (int i = 0; i < numKeys; i++) {
            Key tk = new Key("com.bigswitch.bigsync.internal.store", "" + i);
            k.add(tk);
        }
        return k;
    }
}
