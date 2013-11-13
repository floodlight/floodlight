/*
 * Copyright 2008-2009 LinkedIn, Inc
 * Copyright 2013 Big Switch Networks, Inc.
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

import java.util.Collections;
import java.util.List;

import org.sdnplatform.sync.IInconsistencyResolver;
import org.sdnplatform.sync.Versioned;


/**
 * Resolve inconsistencies based on timestamp in the vector clock
 * @param <T> The type f the versioned object
 */
public class TimeBasedInconsistencyResolver<T>
    implements IInconsistencyResolver<Versioned<T>> {

    public List<Versioned<T>> resolveConflicts(List<Versioned<T>> items) {
        if(items.size() <= 1) {
            return items;
        } else {
            Versioned<T> max = items.get(0);
            long maxTime =
                    ((VectorClock) items.get(0).getVersion()).getTimestamp();
            VectorClock maxClock = ((VectorClock) items.get(0).getVersion());
            for(Versioned<T> versioned: items) {
                VectorClock clock = (VectorClock) versioned.getVersion();
                if(clock.getTimestamp() > maxTime) {
                    max = versioned;
                    maxTime = ((VectorClock) versioned.getVersion()).
                            getTimestamp();
                }
                maxClock = maxClock.merge(clock);
            }
            Versioned<T> maxTimeClockVersioned =
                    new Versioned<T>(max.getValue(), maxClock);
            return Collections.singletonList(maxTimeClockVersioned);
        }
    }

    @Override
    public boolean equals(Object o) {
        if(this == o)
            return true;
        return (o != null && getClass() == o.getClass());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
