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
 * IFilterQueue
 *
 * Maintain a queue to filter out duplicates before pushing data into syncDB,
 * and maintain a queue to receive the data while retrieving it out of the
 * syncDB. The enqueueForward method connects to the packJSON method in
 * ISyncAdapter which then pushes the updates into the syncDB.
 *
 * The enqueueReverse() method is called by unpackJSON, which then populates the
 * reverse queue. Then the subscribeHook() in HAWorker calls the
 * dequeueReverse() method to finally get the updates.
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */
public interface IFilterQueue {

	public boolean dequeueForward();

	public List<String> dequeueReverse();

	public boolean enqueueForward(String value);

	public boolean enqueueReverse(String value);

	public void subscribe(String controllerID);

}
