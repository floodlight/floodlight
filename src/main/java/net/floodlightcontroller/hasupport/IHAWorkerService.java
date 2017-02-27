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

import java.util.Set;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * IHAWorkerService
 *
 * This service can be used to obtain the objects containing the several
 * HAWorker classes, which enable you to call the publish and subscribe hooks of
 * those HAWorkers. This enables any Floodlight module to leverage the HASupport
 * module in order to obtain the updates/state information stored by these
 * HAWorkers.
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public interface IHAWorkerService extends IFloodlightService {

	/**
	 * Retrieve a HAWorker class object that has been registered, using its
	 * serviceName.
	 *
	 * @param serviceName
	 *            : Unique name corresponding to the HAWorker class object
	 * @return The HAWorker class object corresponding to 'serviceName'
	 */

	public IHAWorker getService(String serviceName);

	/**
	 * Get a set of all currently registered HAWorker class object serviceNames.
	 *
	 * @return Set of all HAWorker serviceNames.
	 */

	public Set<String> getWorkerKeys();

	/**
	 * The HAWorker service you want to register such that the controller module
	 * will call publish/subscribe on it periodically.
	 *
	 * Note: refer to LDHAWorker to see how this can be used.
	 *
	 * @param serviceName
	 *            : Unique name corresponding to your HAWorker class object
	 * @param haw
	 *            : Your HAWorker class object.
	 */

	public void registerService(String serviceName, IHAWorker haw);

}
