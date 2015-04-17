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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.sync.IInconsistencyResolver;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.internal.TUtils;
import org.sdnplatform.sync.internal.version.VectorClockInconsistencyResolver;


public class VectorClockInconsistencyResolverTest {

    private IInconsistencyResolver<Versioned<String>> resolver;
    private Versioned<String> later;
    private Versioned<String> prior;
    private Versioned<String> current;
    private Versioned<String> concurrent;
    private Versioned<String> concurrent2;

    @Before
    public void setUp() {
        resolver = new VectorClockInconsistencyResolver<String>();
        current = getVersioned(1, 1, 2, 3);
        prior = getVersioned(1, 2, 3);
        concurrent = getVersioned(1, 2, 3, 3);
        concurrent2 = getVersioned(1, 2, 3, 4);
        later = getVersioned(1, 1, 2, 2, 3);
    }

    private Versioned<String> getVersioned(int... nodes) {
        return new Versioned<String>("my-value", TUtils.getClock(nodes));
    }

    @Test
    public void testEmptyList() {
        assertEquals(0, resolver.resolveConflicts(new ArrayList<Versioned<String>>()).size());
    }

    @Test
    public void testDuplicatesResolve() {
        assertEquals(2, resolver.resolveConflicts(Arrays.asList(concurrent,
                                                                current,
                                                                current,
                                                                concurrent,
                                                                current)).size());
    }

    @Test
    public void testResolveNormal() {
        assertEquals(later, resolver.resolveConflicts(Arrays.asList(current, prior, later)).get(0));
        assertEquals(later, resolver.resolveConflicts(Arrays.asList(prior, current, later)).get(0));
        assertEquals(later, resolver.resolveConflicts(Arrays.asList(later, current, prior)).get(0));
    }

    @Test
    public void testResolveConcurrent() {
        List<Versioned<String>> resolved = resolver.resolveConflicts(Arrays.asList(current,
                                                                                   concurrent,
                                                                                   prior));
        assertEquals(2, resolved.size());
        assertTrue("Version not found", resolved.contains(current));
        assertTrue("Version not found", resolved.contains(concurrent));
    }

    @Test
    public void testResolveLargerConcurrent() {
        assertEquals(3, resolver.resolveConflicts(Arrays.asList(concurrent,
                                                                concurrent2,
                                                                current,
                                                                concurrent2,
                                                                current,
                                                                concurrent,
                                                                current)).size());
    }

    @Test
    public void testResolveConcurrentPairWithLater() {
        Versioned<String> later2 = getVersioned(1, 2, 3, 3, 4, 4);
        List<Versioned<String>> resolved = resolver.resolveConflicts(Arrays.asList(concurrent,
                                                                                   concurrent2,
                                                                                   later2));
        assertEquals(1, resolved.size());
        assertEquals(later2, resolved.get(0));
    }
}
