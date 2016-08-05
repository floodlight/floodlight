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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.projectfloodlight.openflow.types.U64;

/***
 * A static utility class to register flow cookiue AppIds and generating
 * flow cookies for a particular App`
 *
 * An "app" is a module or piece of code that can install flows in a switch.
 * E.g., Forwarding and StaticFlowPusher are apps. An App is identified by a
 * 12 bit integer, the id. Furthermore, an App has a name. The id value must
 * be unique but the same name can be registered for multiple numeric ids.
 * TODO: should we enforce unique names
 *
 * This class is thread-safe.
 *
 * The 64 bit OpenFlow cookie field used in the following way
 * <li> Bit 63 -- 52 (12 bit): the AppId
 * <li> Bit 51 -- 0  (52 bit): user data
 *
 * FIXME: The class should be a singleton. The registration method should
 * return an instance of class. This instance should then be used to generate
 * flow cookies. Ideally, we would also represent a flow cookie as a class
 * instance.
 *
 * @author capveg
 */

public class AppCookie {
    private static final int APP_ID_BITS = 12;
    private static final long APP_ID_MASK = (1L << APP_ID_BITS) - 1;
    private static final int APP_ID_SHIFT = 64 - APP_ID_BITS;

    private static final long USER_MASK = 0x000FFFFFFFFFFFFFL;

    private static ConcurrentMap<Long, String> appIdMap =
            new ConcurrentHashMap<Long, String>();

    /**
     * Returns a mask suitable for matching the app ID within a cookie.
     * @return a mask representing the bits used for the app ID in the cookie
     */
    static public U64 getAppFieldMask() {
        return U64.of(APP_ID_MASK << APP_ID_SHIFT);
    }

    /**
     * Returns a mask suitable for matching the user field within a cookie.
     * @return a mask representing the bits used for user data in the cookie
     */
    static public U64 getUserFieldMask() {
        return U64.of(USER_MASK);
    }
    
    /**
     * Encapsulate an application ID and a user block of stuff into a cookie
     *
     * @param application An ID to identify the application
     * @param user Some application specific data
     * @return a cookie for use in OFFlowMod.setCookie()
     * @throws IllegalStateException if the application has not been registered
     */

    static public U64 makeCookie(long application, long user) {
        if (!appIdMap.containsKey(application)) {
            throw new AppIDNotRegisteredException(application);
        }
        user = user & USER_MASK; // mask to prevent sign extend
        return U64.of((application << APP_ID_SHIFT) | user);
    }

    /**
     * Extract the application id from a flow cookie. Does <em>not</em>
     * check whether the application id is registered. The app ID is 
     * defined by the {@link #getAppFieldMask()} bits
     * @param cookie
     * @return
     */
    static public long extractApp(U64 cookie) {
        return (cookie.getValue() >>> APP_ID_SHIFT) & APP_ID_MASK;
    }

    /**
     * Extract the user portion from a flow cookie, defined
     * by the {@link #getUserFieldMask()} bits
     * @param cookie
     * @return
     */
    static public long extractUser(U64 cookie) {
        return cookie.getValue() & USER_MASK;
    }

    /**
     * A lame attempt to prevent duplicate application ID.
     *
     * @param application
     * @param appName
     * @throws AppIDInUseException
     */
    public static void registerApp(long application, String appName)
        throws AppIDException
    {
        if ((application & APP_ID_MASK) != application) {
            throw new InvalidAppIDValueException(application);
        }
        String oldApp = appIdMap.putIfAbsent(application, appName);
        if (oldApp != null && !oldApp.equals(appName)) {
            throw new AppIDInUseException(application, oldApp, appName);
        }
    }

    /**
     * Retrieves the application name registered for the given application id
     * or null if the application has not been registered
     * @param application
     * @return
     */
    public static String getAppName(long application) {
        return appIdMap.get(application);
    }
}
