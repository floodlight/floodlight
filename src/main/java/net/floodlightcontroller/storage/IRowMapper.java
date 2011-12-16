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

/**
 * Interface for mapping the current row in a result set to an object.
 * This is based on the Spring JDBC support.
 * 
 * @author rob
 */
public interface IRowMapper {

    /** This method must be implemented by the client of the storage API
     * to map the current row in the result set to a Java object.
     * 
     * @param resultSet The result set obtained from a storage source query
     * @return The object created from the data in the result set
     */
    Object mapRow(IResultSet resultSet);
}
