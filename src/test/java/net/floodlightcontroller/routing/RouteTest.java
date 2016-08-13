/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.routing;

import static org.junit.Assert.*;

import org.junit.Test;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.test.FloodlightTestCase;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class RouteTest extends FloodlightTestCase {
    @Test
    public void testCloneable() throws Exception {
        Path r1 = new Path(DatapathId.of(1L), DatapathId.of(2L));
        Path r2 = new Path(DatapathId.of(1L), DatapathId.of(3L));

        assertNotSame(r1, r2);
        assertNotSame(r1.getId(), r2.getId());

        r1 = new Path(DatapathId.of(1L), DatapathId.of(3L));
        r1.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of((short)1)));
        r1.getPath().add(new NodePortTuple(DatapathId.of(2L), OFPort.of((short)1)));
        r1.getPath().add(new NodePortTuple(DatapathId.of(2L), OFPort.of((short)2)));
        r1.getPath().add(new NodePortTuple(DatapathId.of(3L), OFPort.of((short)1)));

        r2.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of((short)1)));
        r2.getPath().add(new NodePortTuple(DatapathId.of(2L), OFPort.of((short)1)));
        r2.getPath().add(new NodePortTuple(DatapathId.of(2L), OFPort.of((short)2)));
        r2.getPath().add(new NodePortTuple(DatapathId.of(3L), OFPort.of((short)1)));

        assertEquals(r1, r2);

        NodePortTuple temp = r2.getPath().remove(0);
        assertNotSame(r1, r2);

        r2.getPath().add(0, temp);
        assertEquals(r1, r2);

        r2.getPath().remove(1);
        temp = new NodePortTuple(DatapathId.of(2L), OFPort.of((short)5));
        r2.getPath().add(1, temp);
        assertNotSame(r1, r2);
    }
}
