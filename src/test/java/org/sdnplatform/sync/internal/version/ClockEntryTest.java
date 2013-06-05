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

package org.sdnplatform.sync.internal.version;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sdnplatform.sync.internal.version.ClockEntry;


/**
 *
 */
public class ClockEntryTest {

    @Test
    public void testEquality() {
        ClockEntry v1 = new ClockEntry((short) 0, 1);
        ClockEntry v2 = new ClockEntry((short) 0, 1);
        assertTrue(v1.equals(v1));
        assertTrue(!v1.equals(null));
        assertEquals(v1, v2);

        v1 = new ClockEntry((short) 0, 1);
        v2 = new ClockEntry((short) 0, 2);
        assertTrue(!v1.equals(v2));

        v1 = new ClockEntry(Short.MAX_VALUE, 256);
        v2 = new ClockEntry(Short.MAX_VALUE, 256);
        assertEquals(v1, v2);
    }

    @Test
    public void testIncrement() {
        ClockEntry v = new ClockEntry((short) 0, 1);
        assertEquals(v.getNodeId(), 0);
        assertEquals(v.getVersion(), 1);
        ClockEntry v2 = v.incremented();
        assertEquals(v.getVersion(), 1);
        assertEquals(v2.getVersion(), 2);
    }

}
