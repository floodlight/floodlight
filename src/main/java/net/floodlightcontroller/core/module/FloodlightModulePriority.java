/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.core.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Set a module priority value so if there are multiple modules that provide
 * a given service, and there is a unique module with maximum priority,
 * we will use that module in preference to lower-priority modules rather
 * than requiring users to manually specify a module to load.  This makes it
 * possible to define a default provider that uses the DEFAULT_PROVIDER priority
 * while the normal modules omit the annotation or specify NORMAL priority.
 * @author readams
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FloodlightModulePriority {
    public enum Priority {
        MINIMUM(0),
        TEST(10),
        EXTRA_LOW(20),
        LOW(30),
        NORMAL(40),
        DEFAULT_PROVIDER(50),
        HIGH(60),
        EXTRA_HIGH(70);

        private final int value;
        
        private Priority(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }

    public Priority value() default Priority.NORMAL;
}
