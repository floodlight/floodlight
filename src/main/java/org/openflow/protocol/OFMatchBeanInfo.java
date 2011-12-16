/**
*    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
*    University
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

package org.openflow.protocol;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;

/**
 * Extra info for how to treat OFMatch as a JavaBean
 * 
 * For some (inane!) reason, using chained setters in OFMatch breaks a lot of the JavaBean defaults.
 * 
 * We don't really use OFMatch as a java bean, but there are a lot of nice XML utils that work for
 * free if OFMatch follows the java bean paradigm.
 * 
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 *
 */

public class OFMatchBeanInfo extends SimpleBeanInfo {

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        List<PropertyDescriptor> descs = new LinkedList<PropertyDescriptor>();
        Field[] fields = OFMatch.class.getDeclaredFields();
        String name;
        for (int i=0; i< fields.length; i++) {
            int mod = fields[i].getModifiers();
            if(Modifier.isFinal(mod) ||     // don't expose static or final fields 
                    Modifier.isStatic(mod))
                continue;
            
            name = fields[i].getName();
            Class<?> type = fields[i].getType();
            
            try {
                descs.add(new PropertyDescriptor(name, 
                        name2getter(OFMatch.class, name), 
                        name2setter(OFMatch.class, name, type)));
            } catch (IntrospectionException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        
        return descs.toArray(new PropertyDescriptor[0]);
    }


    private Method name2setter(Class<OFMatch> c, String name, Class<?> type) {
        String mName = "set" + toLeadingCaps(name);
        Method m = null;
        try {
            m = c.getMethod(mName, new Class[]{ type});
        } catch (SecurityException e) {
            
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return m;
    }

    private Method name2getter(Class<OFMatch> c, String name) {
        String mName= "get" + toLeadingCaps(name);
        Method m = null;
        try {
            m = c.getMethod(mName, new Class[]{});
        } catch (SecurityException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return m;
    }
    
    private String toLeadingCaps(String s) {
        char[] array = s.toCharArray();
        array[0] = Character.toUpperCase(array[0]);
        return String.valueOf(array, 0, array.length);
    }
}
