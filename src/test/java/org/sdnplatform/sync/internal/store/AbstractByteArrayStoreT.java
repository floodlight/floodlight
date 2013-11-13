/*
 * Copyright 2008-2009 LinkedIn, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.sdnplatform.sync.internal.store;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.internal.TUtils;
import org.sdnplatform.sync.internal.store.IStore;
import org.sdnplatform.sync.internal.util.ByteArray;

import com.google.common.collect.Lists;

/**
 *
 */
public abstract class AbstractByteArrayStoreT extends
        AbstractStoreT<ByteArray, byte[]> {

    @Override
    public List<ByteArray> getKeys(int numValues) {
        List<ByteArray> keys = Lists.newArrayList();
        for(byte[] array: this.getByteValues(numValues, 8))
            keys.add(new ByteArray(array));
        return keys;
    }

    @Override
    public List<byte[]> getValues(int numValues) {
        return this.getByteValues(numValues, 10);
    }

    @Override
    protected boolean valuesEqual(byte[] t1, byte[] t2) {
        return TUtils.bytesEqual(t1, t2);
    }

    @Test
    public void testEmptyByteArray() throws Exception {
        IStore<ByteArray, byte[]> store = getStore();
        Versioned<byte[]> bytes = new Versioned<byte[]>(new byte[0]);
        store.put(new ByteArray(new byte[0]), bytes);
        List<Versioned<byte[]>> found = store.get(new ByteArray(new byte[0]));
        assertEquals("Incorrect number of results.", 1, found.size());
        bassertEquals("Get doesn't equal put.", bytes, found.get(0));
    }

}
