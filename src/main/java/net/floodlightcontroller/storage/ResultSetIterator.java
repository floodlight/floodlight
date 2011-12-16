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

import java.util.Iterator;
import java.util.NoSuchElementException;

/** Iterator wrapper for an IResultSet, useful for iterating through query
 * results in an enhanced for (foreach) loop.
 * 
 * Note that the iterator manipulates the state of the underlying IResultSet.
 */
public class ResultSetIterator implements Iterator<IResultSet> {
    private IResultSet resultSet;
    private boolean hasAnother;
    private boolean peekedAtNext;
    
    public ResultSetIterator(IResultSet resultSet) {
        this.resultSet = resultSet;
        this.peekedAtNext = false;
    }
    
    @Override
    public IResultSet next() {
        if (!peekedAtNext) {
            hasAnother = resultSet.next();
        }
        peekedAtNext = false;
        if (!hasAnother)
            throw new NoSuchElementException();
        return resultSet;
    }
    
    @Override
    public boolean hasNext() {
        if (!peekedAtNext) {
            hasAnother = resultSet.next();
            peekedAtNext = true;
        }
        return hasAnother;
    }
    
    /** Row removal is not supported; use IResultSet.deleteRow instead.
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
