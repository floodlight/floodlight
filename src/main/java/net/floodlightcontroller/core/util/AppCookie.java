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

import java.util.Hashtable;
import java.util.Map;

/***
 * Registers/binds applications to a unique ID and generates
 * unique cookies
 *
 */

public class AppCookie {
	
    static final int APP_ID_BITS = 12;
    static final int APP_ID_SHIFT = (Long.SIZE - APP_ID_BITS);
    // we have bits 13-31 unused here ... that's ok!
    static final int USER_BITS = Integer.SIZE;
    static final int USER_SHIFT = 0;

    static final long APP_ID_BIT_MASK = (1L << APP_ID_BITS) - 1;
    static final long USER_BIT_MASK	  = (1L << USER_BITS  ) - 1;
	
	static Map<Class<?>, Integer>  classAppIDMap = new Hashtable<Class<?>, Integer>  ();	// Class to application ID
	static Map<Integer,  Class<?>> appIDClassMap = new Hashtable<Integer,  Class<?>> ();	// Reverse of the above
	static Map<Integer,  Integer>  appIDUserMap  = new Hashtable<Integer,  Integer>  ();	// Application ID/user map

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
        return (int)((cookie>> APP_ID_SHIFT) & APP_ID_BIT_MASK);
    }
    
    static public int extractUser(long cookie) {
        return (int)((cookie>> USER_SHIFT) & USER_BIT_MASK);
    }
    
    /**
     * Returns a 12-bit hash given a class
     * 
     * @param aClass
     * @return 12-bit application identifier
     */
    static protected int applicationFromClass (Class<?> aClass)
    {
    	byte [] nameBytes	= aClass.getName ().getBytes ();
    	int		application = 0;
    	
    	for (byte aByte: nameBytes) {
    		application <<= Byte.SIZE;
    		
    		byte wrappedByte = (byte) (application >> APP_ID_BITS);
    		
    		application &= APP_ID_BIT_MASK;

    		application |= (wrappedByte ^ aByte);
    	}
    	
    	return application;
    }

    /**
     * Returns an application identifier for an application class
     * 
     * @param aClass
     * @return a 12-bit application identifier, or 0 (zero) if <code>aClass</code> is null
     */
    static public int registerClass (Class<?> aClass)
    {
    	int application = 0;
    	
    	if (aClass != null) {
    		Integer appID = classAppIDMap.get (aClass);

    		// Class is unknown
    		if (appID == null) {
    			int	app = applicationFromClass (aClass);
    			
    			while (true) {
    				Class<?> regClass = appIDClassMap.get (app);

    				// New entry
    				if (regClass == null) {
    					classAppIDMap.put (aClass, app);
    					appIDClassMap.put (app, aClass);
    					appIDUserMap .put (app, 1);			// initialize user counter

    					application = app;
    					break;
    				}

    				// Application ID collision; increment and search for the next free slot
    				else {
    					if (++app > APP_ID_BIT_MASK)		// implement a 12-bit wrap around
    						app = 1;
    				}
    			}
    		}
    		
    		// Known class
    		else
    			application = appID;
    	}
    	
    	return application;
    }

    /**
     * Returns whether a cookie's application identifier corresponds to a given application identifier
     * 
     * @param cookie
     * @param application
     * @return whether the cookie's application identifier corresponds to the specified application identifier
     */
    static public boolean isApplication (long cookie, int application)
    {
    	return extractApp (cookie) == application;
    }
    
    /**
     * Returns whether an application identifier is registered
     * 
     * @param application
     * @return whether the application identifier is registered
     */
    static public boolean isRegisteredApplication (int application)
    {
    	Integer userInt	= appIDUserMap.get (application);
    	
    	return (userInt != null);
    }
    
    /**
     * Returns whether a cookie's application identifier is registered
     * 
     * @param cookie
     * @return whether the cookie's application identifier is registered
     */
    static public boolean hasRegisteredApplication (long cookie)
    {
    	return isRegisteredApplication (extractApp (cookie));
    }
    
    /**
     * Returns whether a cookie was generated via <code>makeCookie (int)</code>,
     * <code>makeCookie (Class&lt;?&gt;)</code>, or <code>makeCookie ()</code>
     * 
     * @param cookie
     * @return true if generated; false otherwise
     */
    static public boolean isValidCookie (long cookie)
    {
    	int		application = extractApp  (cookie);
    	Integer userInt		= appIDUserMap.get (application);
    	
    	if (userInt == null)
    		return false;
    	
    	int user = extractUser (cookie);
    	
    	return (user > 0 && user <= userInt);
    }

    /**
     * Returns whether a cookie's application identifier corresponds to a given class
     * 
     * @param cookie
     * @param aClass
     * @return whether the cookie's application identifier corresponds to the specified class
     */
    static public boolean isClass (long cookie, Class<?> aClass)
    {
    	Integer appID = classAppIDMap.get (aClass);
    	
    	return appID != null ? isApplication (cookie, appID) : false;
    }
    
    /**
     * Returns a new cookie given a pre-registered application identifier
     * 
     * @param application
     * @return new application-specific cookie or 0 (zero) if the application ID was unrecognized
     */
    static public long makeCookie (int application)
    {
    	Integer userInt = appIDUserMap.get (application);
    	
    	if (userInt == null)
    		return 0;

    	int	 user	= userInt.intValue ();
    	long cookie = makeCookie (application, user);

    	appIDUserMap.put (application, user + 1);

    	return cookie;
    }
    
    /**
     * Returns a new cookie given an OF application class; provisionally invokes <code>registerClass ()</code>
     * 
     * @param aClass
     * @return new application-specific cookie or 0 (zero) if <code>registerClass ()</code> failed
     */
    static public long makeCookie (Class<?> aClass)
    {
    	Integer appID = classAppIDMap.get (aClass);
    	int		application;
    	
    	if (appID == null) {
    		application = registerClass (aClass);
    		
    		if (application == 0)
    			return 0;
    	}
    	else
    		application = appID;
    	
    	return makeCookie (application);
    }

}
