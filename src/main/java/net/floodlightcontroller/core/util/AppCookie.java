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

/***
 * FIXME Need a system for registering/binding applications to a unique ID
 * 
 * @author capveg
 *
 */

public class AppCookie {
    static final int APP_ID_BITS = 12;
    static final int APP_ID_SHIFT = (64 - APP_ID_BITS);
    /**the following bit will be set accordingly if the field is rewritten by application. e.g. VRS or floating IP */
    static final int SRC_MAC_REWRITE_BIT=13;
    static final int DEST_MAC_REWRITE_BIT=14;
    static final int SRC_IP_REWRITE_BIT=15;
    static final int DEST_IP_REWRITE_BIT=16;

 // we have bits 17-31 unused here ... that's ok!
    static final int USER_BITS = 32;
    static final int USER_SHIFT = 0;

    static final long REWRITE_MASK= 0x000f000000000000L;
    private static ConcurrentHashMap<Integer, String> appIdMap =
            new ConcurrentHashMap<Integer, String>();

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

    static public boolean isRewriteFlagSet(long cookie) {
        if ((cookie & REWRITE_MASK) !=0L)
            return true;
        return false;
    }
    static public boolean isSrcMacRewriteFlagSet(long cookie) {
        if ((cookie & (1L << (64-SRC_MAC_REWRITE_BIT))) !=0L)
            return true;
        return false;
    }
    static public boolean isDestMacRewriteFlagSet(long cookie) {
        if ((cookie & (1L << (64-DEST_MAC_REWRITE_BIT))) !=0L)
            return true;
        return false;
    }
    static public boolean isSrcIpRewriteFlagSet(long cookie) {
        if ((cookie & (1L << (64-SRC_IP_REWRITE_BIT))) !=0L)
            return true;
        return false;
    }
    static public boolean isDestIpRewriteFlagSet(long cookie) {
        if ((cookie & (1L << (64-DEST_IP_REWRITE_BIT))) !=0L)
            return true;
        return false;
    }
    static public long setSrcMacRewriteFlag(long cookie) {
        return cookie | (1L << (64-SRC_MAC_REWRITE_BIT));
    }
    static public long setDestMacRewriteFlag(long cookie) {
        return cookie | (1L << (64-DEST_MAC_REWRITE_BIT));
    }
    static public long setSrcIpRewriteFlag(long cookie) {
        return cookie | (1L << (64-SRC_IP_REWRITE_BIT));
    }
    static public long setDestIpRewriteFlag(long cookie) {
        return cookie | (1L << (64-DEST_IP_REWRITE_BIT));
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
    static public void registerApp(int application, String appName)
        throws AppIDInUseException
    {
        String oldApp = appIdMap.putIfAbsent(application, appName);
        if (oldApp != null) {
            throw new AppIDInUseException(application, oldApp);
        }
    }
}
