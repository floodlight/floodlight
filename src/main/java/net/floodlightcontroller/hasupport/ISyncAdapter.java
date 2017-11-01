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
 * ISyncAdapter
 *
 * This interface specifies the methods used to push to and retrieve updates
 * from the syncDB. packJSON is called by FilterQueue's dequeueForward method
 * and unpackJSON is called by FilterQueue's subscribe method. unpackJSON
 * retrieves the updates and calls Filter Queue's enqueueReverse() in order to
 * later pass them on to the subscribeHook().
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public interface ISyncAdapter {

	public void packJSON(List<String> updates);

	public void unpackJSON(String controllerID);

}
