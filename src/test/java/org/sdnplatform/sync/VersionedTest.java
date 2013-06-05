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

package org.sdnplatform.sync;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.internal.TUtils;
import org.sdnplatform.sync.internal.version.VectorClock;


public class VersionedTest {
    long now = System.currentTimeMillis();

    private Versioned<Integer> getVersioned(Integer value, 
                                            int... versionIncrements) {
        return new Versioned<Integer>(value, 
                                      TUtils.getClockT(now, versionIncrements));
    }

    public void mustHaveVersion() {
        try {
            new Versioned<Integer>(1, null);
            fail("Successfully created Versioned with null version.");
        } catch(NullPointerException e) {
            // this is good
        }
    }

    @Test
    public void testEquals() {
        assertEquals("Null versioneds not equal.", getVersioned(null), getVersioned(null));
        assertEquals("equal versioneds not equal.", getVersioned(1), getVersioned(1));
        assertEquals("equal versioneds not equal.", getVersioned(1, 1, 2), getVersioned(1, 1, 2));

        assertTrue("Equals values with different version are equal!",
                   !getVersioned(1, 1, 2).equals(getVersioned(1, 1, 2, 2)));
        assertTrue("Different values with same version are equal!",
                   !getVersioned(1, 1, 2).equals(getVersioned(2, 1, 2)));
        assertTrue("Different values with different version are equal!",
                   !getVersioned(1, 1, 2).equals(getVersioned(2, 1, 1, 2)));

        // Should work for array types too!
        assertEquals("Equal arrays are not equal!",
                     new Versioned<byte[]>(new byte[] { 1 }, 
                                           new VectorClock(now)),
                     new Versioned<byte[]>(new byte[] { 1 }, 
                                           new VectorClock(now)));
    }

    @Test
    public void testClone() {
        Versioned<Integer> v1 = getVersioned(2, 1, 2, 3);
        Versioned<Integer> v2 = v1.cloneVersioned();
        assertEquals(v1, v2);
        assertTrue(v1 != v2);
        assertTrue(v1.getVersion() != v2.getVersion());
        v2.increment(1, System.currentTimeMillis());
        assertTrue(!v1.equals(v2));
    }

}
