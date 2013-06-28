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
 * <li> Bit 51 -- 32 (20 bit): currently unused. Set to 0.
 * <li> Bit 31 -- 0  (32 bit): user data
 *
 * FIXME: The class should be a singleton. The registration method should
 * return an instance of class. This instance should then be used to generate
 * flow cookies. Ideally, we would also represent a flow cookie as a class
 * instance.
 *
 *
 * @author capveg
 *
 */

public class AppCookie {
    static final int APP_ID_BITS = 12;
    static final long APP_ID_MASK = (1L << APP_ID_BITS) - 1;
    static final int APP_ID_SHIFT = (64 - APP_ID_BITS);

    static final long USER_MASK = 0x00000000FFFFFFFFL;

    /**the following bit will be set accordingly if the field is rewritten by application. e.g. VRS or floating IP
     * FIXME: these should not be in AppCookie and they shoul not use
     * the reserved bit range*/
    static final int SRC_MAC_REWRITE_BIT=33;
    static final int DEST_MAC_REWRITE_BIT=34;
    static final int SRC_IP_REWRITE_BIT=35;
    static final int DEST_IP_REWRITE_BIT=36;


    static final long REWRITE_MASK= 0x000f00000000L;
    private static ConcurrentMap<Integer, String> appIdMap =
            new ConcurrentHashMap<Integer, String>();

    /**
     * Encapsulate an application ID and a user block of stuff into a cookie
     *
     * @param application An ID to identify the application
     * @param user Some application specific data
     * @return a cookie for use in OFFlowMod.setCookie()
     * @throws IllegalStateException if the application has not been registered
     */

    static public long makeCookie(int application, int user) {
        if (!appIdMap.containsKey(application)) {
            throw new AppIDNotRegisteredException(application);
        }
        long longApp = application;
        long longUser = user & USER_MASK; // mask to prevent sign extend
        return (longApp << APP_ID_SHIFT) | longUser;
    }

    /**
     * Extract the application id from a flow cookie. Does <em>not</em> check
     * whether the application id is registered
     * @param cookie
     * @return
     */
    static public int extractApp(long cookie) {
        return (int)((cookie >>> APP_ID_SHIFT) & APP_ID_MASK);
    }

    static public int extractUser(long cookie) {
        return (int)(cookie & USER_MASK);
    }

    static public boolean isRewriteFlagSet(long cookie) {
        if ((cookie & REWRITE_MASK) !=0L)
            return true;
        return false;
    }
    static public boolean isSrcMacRewriteFlagSet(long cookie) {
        if ((cookie & (1L << (SRC_MAC_REWRITE_BIT-1))) !=0L)
            return true;
        return false;
    }
    static public boolean isDestMacRewriteFlagSet(long cookie) {
        if ((cookie & (1L << (DEST_MAC_REWRITE_BIT-1))) !=0L)
            return true;
        return false;
    }
    static public boolean isSrcIpRewriteFlagSet(long cookie) {
        if ((cookie & (1L << (SRC_IP_REWRITE_BIT-1))) !=0L)
            return true;
        return false;
    }
    static public boolean isDestIpRewriteFlagSet(long cookie) {
        if ((cookie & (1L << (DEST_IP_REWRITE_BIT-1))) !=0L)
            return true;
        return false;
    }
    static public long setSrcMacRewriteFlag(long cookie) {
        return cookie | (1L << (SRC_MAC_REWRITE_BIT-1));
    }
    static public long setDestMacRewriteFlag(long cookie) {
        return cookie | (1L << (DEST_MAC_REWRITE_BIT-1));
    }
    static public long setSrcIpRewriteFlag(long cookie) {
        return cookie | (1L << (SRC_IP_REWRITE_BIT-1));
    }
    static public long setDestIpRewriteFlag(long cookie) {
        return cookie | (1L << (DEST_IP_REWRITE_BIT-1));
    }
    /**
     * A lame attempt to prevent duplicate application ID.
     * TODO: Once bigdb is merged, we should expose appID->appName map
     *       via REST API so CLI doesn't need a separate copy of the map.
     *
     * @param application
     * @param appName
     * @throws AppIDInUseException
     */
    public static void registerApp(int application, String appName)
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
    public static String getAppName(int application) {
        return appIdMap.get(application);
    }
}
