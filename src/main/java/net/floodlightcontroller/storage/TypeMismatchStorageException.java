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

public class TypeMismatchStorageException extends StorageException {

    private static final long serialVersionUID = -7923586656854871345L;

    private static String makeExceptionMessage(String requestedType, String actualType, String columnName) {
        if (requestedType == null)
            requestedType = "???";
        if (actualType == null)
            actualType = "???";
        if (columnName == null)
            columnName = "???";
        String message = "The requested type (" + requestedType + ") does not match the actual type (" + actualType + ") of the value for column \"" + columnName + "\".";
        return message;
    }
    
    public TypeMismatchStorageException() {
        super(makeExceptionMessage(null, null, null));
    }
    
    public TypeMismatchStorageException(String requestedType, String actualType, String columnName) {
        super(makeExceptionMessage(requestedType, actualType, columnName));
    }
}
