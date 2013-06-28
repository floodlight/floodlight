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

import static org.junit.Assert.*;

import org.junit.Test;


public class AppCookieTest {
    /* Unfortunately the AppCookie registry is static. So we need to pick
     * app ids that are not otherwise used for testing.
     * NOTE: MSB bit is set for appId and cleared or appId2 ==> allows
     * testing for sign handling
     *
     */
    private static int appId = 0xF42;
    private static int appId2 = 0x743;
    private static int invalidAppId1 = 0x1000;
    private static int invalidAppId2 = -1;

    @Test
    public void testAppCookie(){
        int user = 0xF123F123; // MSB set
        int user2 = 0x42;      // MSB cleared
        long expectedCookie11 =  0xF4200000F123F123L; // app1, user1
        long expectedCookie21 =  0x74300000F123F123L; // app2, user1
        long expectedCookie12 =  0xF420000000000042L; // app1, user2
        long expectedCookie22 =  0x7430000000000042L; // app2, user2
        String name = "FooBar";
        String name2 = "FooFooFoo";


        // try get a cookie or an unregistered appId
        try {
            AppCookie.makeCookie(appId, user);
            fail("Expected exception not thrown");
        } catch(AppIDNotRegisteredException e) { /* expected */ }

        AppCookie.registerApp(appId, name);

        long cookie = AppCookie.makeCookie(appId, user);
        assertEquals(expectedCookie11, cookie);
        assertEquals(appId, AppCookie.extractApp(cookie));
        assertEquals(user, AppCookie.extractUser(cookie));

        cookie = AppCookie.makeCookie(appId, user2);
        assertEquals(expectedCookie12, cookie);
        assertEquals(appId, AppCookie.extractApp(cookie));
        assertEquals(user2, AppCookie.extractUser(cookie));

        // Register again with the same name
        AppCookie.registerApp(appId, name);

        // Register again with different name ==> exception
        try {
            AppCookie.registerApp(appId, name + "XXXXX");
            fail("Expected exception not thrown");
        } catch (AppIDInUseException e) { /* expected */ }

        // try get a cookie or an unregistered appId
        try {
            AppCookie.makeCookie(appId2, user);
            fail("Expected exception not thrown");
        } catch(AppIDNotRegisteredException e) { /* expected */ }

        AppCookie.registerApp(appId2, name2);

        cookie = AppCookie.makeCookie(appId2, user);
        assertEquals(expectedCookie21, cookie);
        assertEquals(appId2, AppCookie.extractApp(cookie));
        assertEquals(user, AppCookie.extractUser(cookie));

        cookie = AppCookie.makeCookie(appId2, user2);
        assertEquals(expectedCookie22, cookie);
        assertEquals(appId2, AppCookie.extractApp(cookie));
        assertEquals(user2, AppCookie.extractUser(cookie));

        // Register invalid app ids
        try {
            AppCookie.registerApp(invalidAppId1, "invalid");
            fail("Expected exception not thrown");
        } catch (InvalidAppIDValueException e) { /* expected */ }

        try {
            AppCookie.registerApp(invalidAppId2, "also invalid");
            fail("Expected exception not thrown");
        } catch (InvalidAppIDValueException e) { /* expected */ }


    }
}
