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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProvider;
import net.floodlightcontroller.storage.IStorageExceptionHandler;

public class TerminationStorageExceptionHandler implements IStorageExceptionHandler {
    protected static Logger log = LoggerFactory.getLogger(TerminationStorageExceptionHandler.class);

    private IFloodlightProvider floodlightProvider;
    
    TerminationStorageExceptionHandler(IFloodlightProvider floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }
    
    @Override
    public void handleException(Exception exc) {
        log.error("Storage exception while asynchronous storage operation; terminating process", exc);
        floodlightProvider.terminate();
    }
}
