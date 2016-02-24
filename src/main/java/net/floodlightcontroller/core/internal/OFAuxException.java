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

package net.floodlightcontroller.core.internal;

/**
 * This exception indicates an error or unexpected message during
 * OF Aux handshaking. E.g., if a switch reports that it cannot supply us
 * with the number of OF Aux connections needed.
 *  @author Jason Parraga <Jason.Parraga@bigswitch.com>
 */
public class OFAuxException extends SwitchStateException{

        private static final long serialVersionUID = 8452081020837079086L;

        public OFAuxException() {
            super();
        }

        public OFAuxException(String arg0) {
            super(arg0);
        }

        public OFAuxException(Throwable arg0) {
            super(arg0);
        }
        
}