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

package net.floodlightcontroller.storage.nosql;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.NullValueStorageException;
import net.floodlightcontroller.storage.ResultSetIterator;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.storage.TypeMismatchStorageException;

public class NoSqlResultSet implements IResultSet {

    NoSqlStorageSource storageSource;
    String tableName;
    String primaryKeyName;
    List<Map<String,Object>> rowList;
    int currentIndex;
    Map<String,Object> currentRowUpdate;
    List<Map<String,Object>> rowUpdateList;
    Set<Object> rowDeleteSet;
    Iterator<IResultSet> resultSetIterator;
    
    NoSqlResultSet(NoSqlStorageSource storageSource, String tableName, List<Map<String,Object>> rowList) {
        this.storageSource = storageSource;
        this.primaryKeyName = storageSource.getTablePrimaryKeyName(tableName);
        this.tableName = tableName;
        if (rowList == null)
            rowList = new ArrayList<Map<String,Object>>();
        this.rowList = rowList;
        currentIndex = -1;
    }
    
    void addRow(Map<String,Object> row) {
        rowList.add(row);
    }
    
    @Override
    public Map<String,Object> getRow() {
        if ((currentIndex < 0) || (currentIndex >= rowList.size())) {
            throw new StorageException("No current row in result set.");
        }
        
        return rowList.get(currentIndex);
    }

    @Override
    public boolean containsColumn(String columnName) {
        return getObject(columnName) != null;
    }
        
    @Override
    public void close() {
    }

    private void endCurrentRowUpdate() {
        if (currentRowUpdate != null) {
            if (rowUpdateList == null)
                rowUpdateList = new ArrayList<Map<String,Object>>();
            rowUpdateList.add(currentRowUpdate);
            currentRowUpdate = null;
        }
    }
    
    @Override
    public boolean next() {
        endCurrentRowUpdate();
        currentIndex++;
        return currentIndex < rowList.size();
    }

    @Override
    public void save() {
        endCurrentRowUpdate();
        
        if (rowUpdateList != null) {
            storageSource.updateRows(tableName, rowUpdateList);
            rowUpdateList = null;
        }
        
        if (rowDeleteSet != null) {
            storageSource.deleteRows(tableName, rowDeleteSet);
            rowDeleteSet = null;
        }
    }

    Object getObject(String columnName) {
        Map<String,Object> row = rowList.get(currentIndex);
        Object value = row.get(columnName);
        return value;
    }
    
    @Override
    public boolean getBoolean(String columnName) {
        Boolean b = getBooleanObject(columnName);
        if (b == null)
            throw new NullValueStorageException(columnName);
        return b.booleanValue();
    }

    @Override
    public byte getByte(String columnName) {
        Byte b = getByteObject(columnName);
        if (b == null)
            throw new NullValueStorageException(columnName);
        return b.byteValue();
    }

    @Override
    public byte[] getByteArray(String columnName) {
        byte[] b = null;
        Object obj = getObject(columnName);
        if (obj != null) {
            if (!(obj instanceof byte[]))
                throw new StorageException("Invalid byte array value");
            b = (byte[])obj;
        }
        return b;
    }

    @Override
    public double getDouble(String columnName) {
        Double d = getDoubleObject(columnName);
        if (d == null)
            throw new NullValueStorageException(columnName);
        return d.doubleValue();
    }

    @Override
    public float getFloat(String columnName) {
        Float f = getFloatObject(columnName);
        if (f == null)
            throw new NullValueStorageException(columnName);
        return f.floatValue();
    }

    @Override
    public int getInt(String columnName) {
        Integer i = getIntegerObject(columnName);
        if (i == null)
            throw new NullValueStorageException(columnName);
        return i.intValue();
    }

    @Override
    public long getLong(String columnName) {
        Long l = getLongObject(columnName);
        if (l == null)
            throw new NullValueStorageException(columnName);
        return l.longValue();
    }

    @Override
    public short getShort(String columnName) {
        Short s = getShortObject(columnName);
        if (s == null)
            throw new NullValueStorageException(columnName);
        return s.shortValue();
    }

    @Override
    public String getString(String columnName) {
        Object obj = getObject(columnName);
        if (obj == null)
            return null;
        return obj.toString();
    }

    @Override
    public Date getDate(String column) {
        Date d;
        Object obj = getObject(column);
        if (obj == null) {
            d = null;
        } else if (obj instanceof Date) {
            d = (Date) obj;
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            try {
                d = dateFormat.parse(obj.toString());
            }
            catch (ParseException exc) {
                throw new TypeMismatchStorageException(Date.class.getName(), obj.getClass().getName(), column);
            }
        }
        return d;
    }


    @Override
    public Short getShortObject(String columnName)
    {
        Short s;
        Object obj = getObject(columnName);
        if (obj instanceof Short) {
            s = (Short)obj;
        } else if (obj != null) {
            try {
                s = Short.parseShort(obj.toString());
            }
            catch (NumberFormatException exc) {
                throw new TypeMismatchStorageException(Short.class.getName(), obj.getClass().getName(), columnName);
            }
        } else {
            s = null;
        }
        return s;
    }
    
    @Override
    public Integer getIntegerObject(String columnName)
    {
        Integer i;
        Object obj = getObject(columnName);
        if (obj instanceof Integer) {
            i = (Integer)obj;
        } else if (obj != null) {
            try {
                i = Integer.parseInt(obj.toString());
            }
            catch (NumberFormatException exc) {
                throw new TypeMismatchStorageException(Integer.class.getName(), obj.getClass().getName(), columnName);
            }
        } else {
            i = null;
        }
        return i;
    }

    @Override
    public Long getLongObject(String columnName)
    {
        Long l;
        Object obj = getObject(columnName);
        if (obj instanceof Long) {
            l = (Long)obj;
        } else if (obj != null) {
            try {
                l = Long.parseLong(obj.toString());
            }
            catch (NumberFormatException exc) {
                throw new TypeMismatchStorageException(Long.class.getName(), obj.getClass().getName(), columnName);
            }
        } else {
            l = null;
        }
        return l;
    }

    @Override
    public Float getFloatObject(String columnName)
    {
        Float f;
        Object obj = getObject(columnName);
        if (obj instanceof Float) {
            f = (Float)obj;
        } else if (obj != null) {
            try {
                f = Float.parseFloat(obj.toString());
            }
            catch (NumberFormatException exc) {
                throw new TypeMismatchStorageException(Float.class.getName(), obj.getClass().getName(), columnName);
            }
        } else {
            f = null;
        }
        return f;
    }

    @Override
    public Double getDoubleObject(String columnName)
    {
        Double d;
        Object obj = getObject(columnName);
        if (obj instanceof Double) {
            d = (Double)obj;
        } else if (obj != null) {
            try {
                d = Double.parseDouble(obj.toString());
            }
            catch (NumberFormatException exc) {
                throw new TypeMismatchStorageException(Double.class.getName(), obj.getClass().getName(), columnName);
            }
        } else {
            d = null;
        }
        return d;
    }

    @Override
    public Boolean getBooleanObject(String columnName)
    {
        Boolean b;
        Object obj = getObject(columnName);
        if (obj instanceof Boolean) {
            b = (Boolean)obj;
        } else if (obj != null) {
            try {
                b = Boolean.parseBoolean(obj.toString());
            }
            catch (NumberFormatException exc) {
                throw new TypeMismatchStorageException(Boolean.class.getName(), obj.getClass().getName(), columnName);
            }
        } else {
            b = null;
        }
        return b;
    }

    @Override
    public Byte getByteObject(String columnName)
    {
        Byte b;
        Object obj = getObject(columnName);
        if (obj instanceof Byte) {
            b = (Byte)obj;
        } else if (obj != null) {
            try {
                b = Byte.parseByte(obj.toString());
            }
            catch (NumberFormatException exc) {
                throw new TypeMismatchStorageException(Byte.class.getName(), obj.getClass().getName(), columnName);
            }
        } else {
            b = null;
        }
        return b;
    }

    
    @Override
    public boolean isNull(String columnName)
    {
        Object obj = getObject(columnName);
        return (obj == null);
    }

    private void addRowUpdate(String column, Object value) {
        if (currentRowUpdate == null) {
            currentRowUpdate = new HashMap<String,Object>();
            Object key = rowList.get(currentIndex).get(primaryKeyName);
            currentRowUpdate.put(primaryKeyName, key);
        }
        currentRowUpdate.put(column, value);
    }
    
    @Override
    public void setBoolean(String columnName, boolean value) {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setByte(String columnName, byte value) {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setByteArray(String columnName, byte[] byteArray) {
        addRowUpdate(columnName, byteArray);
    }

    @Override
    public void setDouble(String columnName, double value) {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setFloat(String columnName, float value) {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setInt(String columnName, int value) {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setLong(String columnName, long value) {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setShort(String columnName, short value) {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setString(String columnName, String value) {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setShortObject(String columnName, Short value)
    {
        addRowUpdate(columnName, value);
    }
    
    @Override
    public void setIntegerObject(String columnName, Integer value)
    {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setLongObject(String columnName, Long value)
    {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setFloatObject(String columnName, Float value)
    {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setDoubleObject(String columnName, Double value)
    {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setBooleanObject(String columnName, Boolean value)
    {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setByteObject(String columnName, Byte value)
    {
        addRowUpdate(columnName, value);
    }

    @Override
    public void setDate(String column, Date value) {
        addRowUpdate(column, value);
    }

    
    public void setNull(String columnName)
    {
        addRowUpdate(columnName, null);
    }

    
    @Override
    public void deleteRow() {
        Object key = (String) rowList.get(currentIndex).get(primaryKeyName);
        if (rowDeleteSet == null)
            rowDeleteSet = new HashSet<Object>();
        rowDeleteSet.add(key);
    }
    
    @Override
    public Iterator<IResultSet> iterator() {
        if (resultSetIterator == null)
            resultSetIterator = new ResultSetIterator(this);
        return resultSetIterator;
    }
}
