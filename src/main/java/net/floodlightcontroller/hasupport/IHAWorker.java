/**
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

package net.floodlightcontroller.hasupport;

import java.util.List;

/**
 * IHAWorker
 *
 * This interface describes the functions which are necessary if you want to
 * implement a new HAWorker class in addition to the Topology, Link Discovery
 * etc. HAWorkers that already exist. The publish hook connects to the
 * IFilterQueue's enqueueForward, and dequeueForward method in order to populate
 * the queue and push to the syncDB. Similarly, the subscribe hook uses the
 * dequeueReverse method to retrieve updates from the syncDB.
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public interface IHAWorker {

	public List<String> assembleUpdate();

	public boolean publishHook();

	public List<String> subscribeHook(String controllerID);

}
