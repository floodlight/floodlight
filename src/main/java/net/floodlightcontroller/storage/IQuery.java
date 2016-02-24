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

/** Representation of a database query. For SQL queries this maps to
 * a prepared statement, so it will be more efficient than if you use the
 * methods in IStorageSource that bypass the IQuery. For many NoSQL
 * storage sources there won't be any performance improvement from keeping
 * around the query.
 * 
 * The query interface also supports parameterized queries (i.e. which maps
 * to using ? values in a SQL query). The values of the parameters are set
 * using the setParameter method. In the storage source API the parameters
 * are named rather than positional. The format of the parameterized values
 * in the query predicates is the parameter name bracketed with question marks
 * (e.g. ?MinimumSalary? ).
 * 
 * @author rob
 *
 */
public interface IQuery {
    String getTableName();
    void setParameter(String name, Object value);
}
