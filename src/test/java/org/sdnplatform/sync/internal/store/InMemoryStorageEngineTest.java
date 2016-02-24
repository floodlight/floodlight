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

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.sdnplatform.sync.internal.TUtils;
import org.sdnplatform.sync.internal.store.IStorageEngine;
import org.sdnplatform.sync.internal.store.InMemoryStorageEngine;
import org.sdnplatform.sync.internal.util.ByteArray;


public class InMemoryStorageEngineTest extends AbstractStorageEngineT {

    private IStorageEngine<ByteArray, byte[]> store;

    @Override
    public IStorageEngine<ByteArray, byte[]> getStorageEngine() {
        return store;
    }

    @Before
    public void setUp() throws Exception {
        this.store = new InMemoryStorageEngine<ByteArray, byte[]>("test");
    }

    @Override
    public List<ByteArray> getKeys(int numKeys) {
        List<ByteArray> keys = new ArrayList<ByteArray>(numKeys);
        for(int i = 0; i < numKeys; i++)
            keys.add(new ByteArray(TUtils.randomBytes(10)));
        return keys;
    }

}
