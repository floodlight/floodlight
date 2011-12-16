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

package net.floodlightcontroller.core.util;

/***
 * FIXME Need a system for registering/binding applications to a unique ID
 * 
 * @author capveg
 *
 */

public class AppCookie {
    static final int APP_ID_BITS = 12;
    static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
    // we have bits 13-31 unused here ... that's ok!
    static final int USER_BITS = 32;
    static final int USER_SHIFT = 0;


    /**
     * Encapsulate an application ID and a user block of stuff into a cookie
     * 
     * @param application An ID to identify the application
     * @param user Some application specific data
     * @return a cookie for use in OFFlowMod.setCookie()
     */
    
    static public long makeCookie(int application, int user) {
        return ((application & ((1L << APP_ID_BITS) - 1)) << APP_ID_SHIFT) | user;
    }
    
    static public int extractApp(long cookie) {
        return (int)((cookie>> APP_ID_SHIFT) & ((1L << APP_ID_BITS) - 1));
    }
    
    static public int extractUser(long cookie) {
        return (int)((cookie>> USER_SHIFT) & ((1L << USER_BITS) - 1));
    }
}
