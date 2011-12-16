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

import java.util.Date;
import java.util.Map;

/** Interface to iterate over the results from a storage query.
 * 
 * @author rob
 *
 */
public interface IResultSet extends Iterable<IResultSet> {
    
    /** This should be called when the client is done using the result set.
     * This will release any underlying resources (e.g. a database connection),
     * which you don't want to wait for or rely on finalizers to release.
     */
    public void close();
    
    /** Advance to the next row in the result set. 
     * @return Returns true if there are more rows to process
     * (i.e. if there's a valid current row) and false if there are no more
     * rows in the result set.
     */
    public boolean next();
    
    /** Save/commit any pending updates to the data in the result set.
     * This must be called after any calls to the set methods or deleting rows
     * for the changes to be applied/committed to the storage source. Note that
     * this doesn't need to be called after each set method or even after each
     * row. It is typically called at the end after updating all of the
     * rows in the result set.
     */
    public void save();
    
    /** Get the current row in the result set. This returns all of the
     * columns in the current row.
     * @return Map containing all of the columns in the current row, indexed
     * by the column name.
     */
    public Map<String,Object> getRow();
    
    /** Delete the current row in the result set.
     */
    public void deleteRow();
    
    public boolean containsColumn(String columnName);
    
    public String getString(String columnName);
    public short getShort(String columnName);
    public int getInt(String columnName);
    public long getLong(String columnName);
    public float getFloat(String columnName);
    public double getDouble(String columnName);
    public boolean getBoolean(String columnName);
    public byte getByte(String columnName);
    public byte[] getByteArray(String columnName);
    public Date getDate(String columnName);
    
    public Short getShortObject(String columnName);
    public Integer getIntegerObject(String columnName);
    public Long getLongObject(String columnName);
    public Float getFloatObject(String columnName);
    public Double getDoubleObject(String columnName);
    public Boolean getBooleanObject(String columnName);
    public Byte getByteObject(String columnName);
    
    public boolean isNull(String columnName);
    
    public void setString(String columnName, String value);
    public void setShort(String columnName, short value);
    public void setInt(String columnName, int value);
    public void setLong(String columnName, long value);
    public void setFloat(String columnName, float value);
    public void setDouble(String columnName, double value);
    public void setBoolean(String columnName, boolean value);
    public void setByte(String columnName, byte value);
    public void setByteArray(String columnName, byte[] byteArray);
    public void setDate(String columnName, Date date);
    
    public void setShortObject(String columnName, Short value);
    public void setIntegerObject(String columnName, Integer value);
    public void setLongObject(String columnName, Long value);
    public void setFloatObject(String columnName, Float value);
    public void setDoubleObject(String columnName, Double value);
    public void setBooleanObject(String columnName, Boolean value);
    public void setByteObject(String columnName, Byte value);
    
    public void setNull(String columnName);
}
