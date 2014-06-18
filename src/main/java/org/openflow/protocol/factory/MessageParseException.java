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

package org.openflow.protocol.factory;

/**
 * Exception thrown when an openflow message fails to parse properly
 */
public class MessageParseException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = -75893812926304726L;

    public MessageParseException() {
        super();
    }

    public MessageParseException(String message, Throwable cause) {
        super(message, cause);
        this.setStackTrace(cause.getStackTrace());
    }

    public MessageParseException(String message) {
        super(message);
    }

    public MessageParseException(Throwable cause) {
        super(cause);
        this.setStackTrace(cause.getStackTrace());
    }
}
