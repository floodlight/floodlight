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

package net.floodlightcontroller.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public enum BundleAction {
    START,
    STOP,
    UNINSTALL,
    REFRESH;

    public static List<BundleAction> getAvailableActions(BundleState state) {
        List<BundleAction> actions = new ArrayList<BundleAction>();
        if (Arrays.binarySearch(new BundleState[] {
                BundleState.ACTIVE, BundleState.STARTING,
                BundleState.UNINSTALLED }, state) < 0) {
            actions.add(START);
        }
        if (Arrays.binarySearch(new BundleState[] {
                BundleState.ACTIVE}, state) >= 0) {
            actions.add(STOP);
        }
        if (Arrays.binarySearch(new BundleState[] {
                BundleState.UNINSTALLED}, state) < 0) {
            actions.add(UNINSTALL);
        }

        // Always capable of refresh?
        actions.add(REFRESH);
        return actions;
    }
}
