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

package net.floodlightcontroller.storage;

public class StorageException extends RuntimeException {

    static final long serialVersionUID = 7839989010156155681L;
    
    static private String makeExceptionMessage(String s) {
        String message = "Storage Exception";
        if (s != null) {
            message += ": ";
            message += s;
        }
        return message;
    }

    public StorageException() {
        super(makeExceptionMessage(null));
    }
    
    public StorageException(String s) {
        super(makeExceptionMessage(s));
    }
    
    public StorageException(String s, Throwable exc) {
        super(makeExceptionMessage(s), exc);
    }
}
