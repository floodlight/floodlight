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

import java.lang.Class;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.storage.AbstractStorageSource;
import net.floodlightcontroller.storage.CompoundPredicate;
import net.floodlightcontroller.storage.IPredicate;
import net.floodlightcontroller.storage.IQuery;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.RowOrdering;
import net.floodlightcontroller.storage.StorageException;
import net.floodlightcontroller.storage.StorageSourceNotification;
import net.floodlightcontroller.storage.TypeMismatchStorageException;

public abstract class NoSqlStorageSource extends AbstractStorageSource {
    protected static Logger log = LoggerFactory.getLogger(NoSqlStorageSource.class);

    public enum ColumnIndexMode { NOT_INDEXED, RANGE_INDEXED, EQUALITY_INDEXED };
    
    protected static final String DEFAULT_PRIMARY_KEY_NAME = "id";
    
    private Map<String,String> tablePrimaryKeyMap = new HashMap<String,String>();
    private Map<String, Map<String,ColumnIndexMode>> tableIndexedColumnMap =
        new HashMap<String,Map<String,ColumnIndexMode>>();
    
    abstract class NoSqlPredicate {

        public boolean incorporateComparison(String columnName,
                OperatorPredicate.Operator operator, Comparable<?> value,
                CompoundPredicate.Operator parentOperator) {
            return false;
        }
        
        public boolean canExecuteEfficiently() {
            return false;
        }
        
        public List<Map<String,Object>> execute(String[] columnNames) {
            assert(false);
            return null;
        }
        
        abstract public boolean matchesRow(Map<String,Object> row);
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    class NoSqlRangePredicate extends NoSqlPredicate {
        NoSqlStorageSource storageSource;
        String tableName;
        String columnName;
        Comparable<?> startValue;
        boolean startInclusive;
        Comparable<?> endValue;
        boolean endInclusive;
        
        NoSqlRangePredicate(NoSqlStorageSource storageSource, String tableName,
                String columnName, Comparable<?> startValue, boolean startInclusive,
                Comparable<?> endValue, boolean endInclusive) {
            this.storageSource = storageSource;
            this.tableName = tableName;
            this.columnName = columnName;
            this.startValue = startValue;
            this.startInclusive = startInclusive;
            this.endValue = endValue;
            this.endInclusive = endInclusive;
        }
        
        public boolean incorporateComparison(String columnName,
                OperatorPredicate.Operator operator, Comparable<?> value,
                CompoundPredicate.Operator parentOperator) {
            
            assert(operator != null);
            assert(parentOperator != null);
            
            // Must be the same column to incorporate
            if (!this.columnName.equals(columnName))
                return false;
            
            // The only time we allow a null value is if it's an EQ operator.
            // In that case we can only incorporate if this predicate is also
            // a null equality predicate.
            if (value == null) {
                return ((operator == OperatorPredicate.Operator.EQ) &&
                        (startValue == null) && (endValue == null) &&
                        startInclusive && endInclusive);
            }
            
            // Don't incorporate parameterized values
            if (value instanceof String) {
                String s = (String)value;
                if (s.startsWith("?") && s.endsWith("?")) {
                    return false;
                }
            }
            
            if (parentOperator == CompoundPredicate.Operator.AND) {
                switch (operator) {
                case EQ:
                    if (matchesValue(value)) {
                        startValue = endValue = value;
                        startInclusive = endInclusive = true;
                        return true;
                    }
                    break;
                case LT:
                    if ((endValue == null) || (((Comparable)value).compareTo(endValue) <= 0)) {
                        endValue = value;
                        endInclusive = false;
                        return true;
                    }
                    break;
                case LTE:
                    if ((endValue == null) || (((Comparable)value).compareTo(endValue) < 0)) {
                        endValue = value;
                        endInclusive = true;
                        return true;
                    }
                    break;
                case GT:
                    if ((startValue == null) || (((Comparable)value).compareTo(startValue) >= 0)) {
                        startValue = value;
                        startInclusive = false;
                        return true;
                    }
                    break;
                case GTE:
                    if ((startValue == null) || (((Comparable)value).compareTo(startValue) > 0)) {
                        startValue = value;
                        startInclusive = true;
                        return true;
                    }
                    break;
                }
            } else {
                switch (operator) {
                case EQ:
                    if (matchesValue(value))
                        return true;
                    break;
                case LT:
                    if ((endValue == null) || (((Comparable)value).compareTo(endValue) > 0)) {
                        endValue = value;
                        endInclusive = false;
                        return true;
                    }
                    break;
                case LTE:
                    if ((endValue == null) || (((Comparable)value).compareTo(endValue) >= 0)) {
                        endValue = value;
                        endInclusive = true;
                        return true;
                    }
                    break;
                case GT:
                    if ((startValue == null) || (((Comparable)value).compareTo(startValue) < 0)) {
                        startValue = value;
                        startInclusive = false;
                        return true;
                    }
                    break;
                case GTE:
                    if ((startValue == null) || (((Comparable)value).compareTo(startValue) <= 0)) {
                        startValue = value;
                        startInclusive = true;
                        return true;
                    }
                    break;
                }
            }
            
            return false;
        }

        private boolean isEqualityRange() {
            return (startValue == endValue) && startInclusive && endInclusive;
        }
        
        public boolean canExecuteEfficiently() {
            ColumnIndexMode indexMode = storageSource.getColumnIndexMode(tableName, columnName);
            switch (indexMode) {
            case NOT_INDEXED:
                return false;
            case RANGE_INDEXED:
                return true;
            case EQUALITY_INDEXED:
                return isEqualityRange();
            }
            return true;
        }

        public List<Map<String,Object>> execute(String columnNameList[]) {
            List<Map<String,Object>> rowList;
            if (isEqualityRange())
                rowList = storageSource.executeEqualityQuery(tableName, columnNameList, columnName, startValue);
            else
                rowList = storageSource.executeRangeQuery(tableName, columnNameList, columnName,
                        startValue, startInclusive, endValue, endInclusive);
                
            return rowList;
        }
        
        Comparable<?> coerceValue(Comparable<?> value, Class targetClass) {
            
            if (value == null)
                return null;
            
            if (value.getClass() == targetClass)
                return value;
            
            // FIXME: For now we convert by first converting the source value to a
            // string and then converting to the target type. This logic probably needs
            // another pass to make it more robust/optimized.
            
            String s = value.toString();
            Comparable<?> obj = null;
            
            try {
                if (targetClass == Integer.class) {
                    obj = new Integer(s);
                } else if (targetClass == Long.class) {
                    obj = new Long(s);
                } else if (targetClass == Short.class) {
                    obj = new Short(s);
                } else if (targetClass == Boolean.class) {
                    obj = new Boolean(s);
                } else if (targetClass == Float.class) {
                    obj = new Float(s);
                } else if (targetClass == Double.class) {
                    obj = new Double(s);
                } else if (targetClass == Byte.class) {
                    obj = new Byte(s);
                } else if (targetClass == String.class) {
                    obj = s;
                } else if (targetClass == Date.class) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                    try {
                        obj = dateFormat.parse(s);
                    }
                    catch (ParseException exc) {
                        throw new TypeMismatchStorageException(Date.class.getName(), value.getClass().getName(), "???");
                    }
                }
            }
            catch (Exception exc) {
                // Ignore the exception here. In this case obj will not be set, so we'll
                // throw the StorageException below when we check for a null obj.
            }
            
            if (obj == null)
                throw new StorageException("Column value could not be coerced to the correct type");
            
            return obj;
        }
        
        boolean matchesValue(Comparable<?> value) {
            boolean isNullEqPredicate = (startValue == null) && (endValue == null) && startInclusive && endInclusive;
            if (value == null)
                return isNullEqPredicate;

            if (isNullEqPredicate)
                return false;
            
            int result;
            Comparable<?> coercedValue;
            if (startValue != null) {
                coercedValue = coerceValue(value, startValue.getClass());
                result = ((Comparable)coercedValue).compareTo(startValue);
                if ((result < 0) || (!startInclusive && (result == 0)))
                    return false;
            }
            if (endValue != null) {
                coercedValue = coerceValue(value, endValue.getClass());
                result = ((Comparable)coercedValue).compareTo(endValue);
                if ((result > 0) || (!endInclusive && (result == 0)))
                    return false;
            }
            return true;
        }
        
        public boolean matchesRow(Map<String,Object> row) {
            Comparable value = (Comparable)row.get(columnName);
            return matchesValue(value);
        }
    }
    
    class NoSqlOperatorPredicate extends NoSqlPredicate {
        
        NoSqlStorageSource storageSource;
        String columnName;
        OperatorPredicate.Operator operator;
        Object value;
        
        NoSqlOperatorPredicate(NoSqlStorageSource storageSource, String columnName,
                OperatorPredicate.Operator operator, Object value) {
            this.storageSource = storageSource;
            this.columnName = columnName;
            this.operator = operator;
            this.value = value;
        }

        public boolean incorporateComparison(String columnName,
                OperatorPredicate.Operator operator, Comparable<?> value,
                CompoundPredicate.Operator parentOperator) {
            return false;
        }

        public boolean canExecuteEfficiently() {
            return false;
        }

        public List<Map<String,Object>> execute(String columnNames[]) {
            throw new StorageException("Unimplemented predicate.");
        }
        
        public boolean matchesRow(Map<String,Object> row) {
            return false;
        }
    }
    
    class NoSqlCompoundPredicate extends NoSqlPredicate {
        
        NoSqlStorageSource storageSource;
        CompoundPredicate.Operator operator;
        boolean negated;
        List<NoSqlPredicate> predicateList;
        
        NoSqlCompoundPredicate(NoSqlStorageSource storageSource, CompoundPredicate.Operator operator,
                boolean negated, List<NoSqlPredicate> predicateList) {
            this.storageSource = storageSource;
            this.operator = operator;
            this.negated = negated;
            this.predicateList = predicateList;
        }

        public boolean incorporateComparison(String columnName,
                OperatorPredicate.Operator operator, Comparable<?> value,
                CompoundPredicate.Operator parentOperator) {
            // It may be possible to incorporate other operator predicate into this one,
            // but it would need to take into account the negated attribute and I'd need
            // to think about it some more to make sure it was correct, so for now we just
            // disallow incorporation
            //if (parentOperator == this.operator) {
            //    for (NoSqlPredicate predicate: predicateList) {
            //        if (predicate.incorporateComparison(columnName, operator, value, parentOperator))
            //            return true;
            //    }
            //}
            return false;
        }

        public boolean canExecuteEfficiently() {
            if (operator == CompoundPredicate.Operator.AND) {
                for (NoSqlPredicate predicate: predicateList) {
                    if (predicate.canExecuteEfficiently()) {
                        return true;
                    }
                }
                return false;
            } else {
                for (NoSqlPredicate predicate: predicateList) {
                    if (!predicate.canExecuteEfficiently()) {
                        return false;
                    }
                }
                return true;
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        class RowComparator implements Comparator<Map<String,Object>> {
            private String primaryKeyName;
            
            public RowComparator(String primaryKeyName) {
                this.primaryKeyName = primaryKeyName;
            }
            
            public int compare(Map<String,Object> row1, Map<String,Object> row2) {
                Comparable key1 = (Comparable)row1.get(primaryKeyName);
                Comparable key2 = (Comparable)row2.get(primaryKeyName);
                return key1.compareTo(key2);
            }
            
            public boolean equals(Object obj) {
                if (!(obj instanceof RowComparator))
                    return false;
                RowComparator rc = (RowComparator)obj;
                if (rc.primaryKeyName == null)
                    return this.primaryKeyName == null;
                return rc.primaryKeyName.equals(this.primaryKeyName);
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private List<Map<String,Object>> combineRowLists(String primaryKeyName,
                List<Map<String,Object>> list1, List<Map<String,Object>> list2,
                CompoundPredicate.Operator operator) {
            ArrayList<Map<String,Object>> combinedRowList = new ArrayList<Map<String,Object>>();
            RowComparator rc = new RowComparator(primaryKeyName);
            Collections.sort(list1, rc);
            Collections.sort(list2,rc);
            
            Iterator<Map<String,Object>> iterator1 = list1.iterator();
            Iterator<Map<String,Object>> iterator2 = list2.iterator();
            boolean update1 = true;
            boolean update2 = true;
            Map<String,Object> row1 = null;
            Map<String,Object> row2 = null;
            Comparable<?> key1 = null;
            Comparable<?> key2 = null;
            
            while (true) {
                if (update1) {
                    if (iterator1.hasNext()) {
                        row1 = iterator1.next();
                        key1 = (Comparable<?>)row1.get(primaryKeyName);
                    } else {
                        row1 = null;
                    }
                }
                if (update2) {
                    if (iterator2.hasNext()) {
                        row2 = iterator1.next();
                        key2 = (Comparable<?>)row2.get(primaryKeyName);
                    } else {
                        row2 = null;
                    }
                }
                if (operator == CompoundPredicate.Operator.AND) {
                    if ((row1 == null) || (row2 == null))
                        break;
                    if (key1.equals(key2))
                        combinedRowList.add(row1);
                } else {
                    if (row1 == null) {
                        if (row2 == null)
                            break;
                        combinedRowList.add(row2);
                    } else if ((row2 == null) || (((Comparable)key1).compareTo(key2) <= 0)) {
                        combinedRowList.add(row2);
                    } else {
                        combinedRowList.add(row1);
                    }
                }
                
                update1 = (key2 == null) || (((Comparable)key1).compareTo(key2) <= 0);
                update2 = (key1 == null) || (((Comparable)key2).compareTo(key1) <= 0);
            }
            
            return combinedRowList;
        }
        
        public List<Map<String,Object>> execute(String columnNames[]) {
            List<Map<String,Object>> combinedRowList = null;
            for (NoSqlPredicate predicate: predicateList) {
                List<Map<String,Object>> rowList = predicate.execute(columnNames);
                if (combinedRowList != null) {
                    combinedRowList = combineRowLists("id", combinedRowList, rowList, operator);
                } else {
                    combinedRowList = rowList;
                }
            }
            return combinedRowList;
        }

        public boolean matchesRow(Map<String,Object> row) {
            if (operator == CompoundPredicate.Operator.AND) {
                for (NoSqlPredicate predicate : predicateList) {
                    if (!predicate.matchesRow(row))  {
                        return false;
                    }
                }
                return true;
            } else {
                for (NoSqlPredicate predicate : predicateList) {
                    if (predicate.matchesRow(row))  {
                        return true;
                    }
                }
                return false;
                
            }
        }
    }
    
    public NoSqlStorageSource() {
        super();
    }
    
    @Override
    public void createTable(String tableName, Set<String> indexedColumns) {
        super.createTable(tableName, indexedColumns);
        if (indexedColumns == null) return;
        for (String columnName : indexedColumns) {
            setColumnIndexMode(tableName, columnName,
                               ColumnIndexMode.EQUALITY_INDEXED);
        }
    }

    public void setTablePrimaryKeyName(String tableName, String primaryKeyName) {
        if ((tableName == null) || (primaryKeyName == null))
            throw new NullPointerException();
        tablePrimaryKeyMap.put(tableName, primaryKeyName);
    }
    
    protected String getTablePrimaryKeyName(String tableName) {
        String primaryKeyName = tablePrimaryKeyMap.get(tableName);
        if (primaryKeyName == null)
            primaryKeyName = DEFAULT_PRIMARY_KEY_NAME;
        return primaryKeyName;
    }
    
    protected ColumnIndexMode getColumnIndexMode(String tableName, String columnName) {
        ColumnIndexMode columnIndexMode = null;
        Map<String, ColumnIndexMode> indexedColumnMap = tableIndexedColumnMap.get(tableName);
        if (indexedColumnMap != null)
            columnIndexMode = indexedColumnMap.get(columnName);
        if (columnIndexMode == null)
            return ColumnIndexMode.NOT_INDEXED;
        return columnIndexMode;
    }
    
    public void setColumnIndexMode(String tableName, String columnName, ColumnIndexMode indexMode) {
        Map<String, ColumnIndexMode> indexedColumnMap = tableIndexedColumnMap.get(tableName);
        if (indexedColumnMap == null) {
            indexedColumnMap = new HashMap<String,ColumnIndexMode>();
            tableIndexedColumnMap.put(tableName, indexedColumnMap);
        }
        indexedColumnMap.put(columnName, indexMode);
    }
    
    Comparable<?> getOperatorPredicateValue(OperatorPredicate predicate, Map<String,Comparable<?>> parameterMap) {
        Comparable<?> value = predicate.getValue();
        if (value instanceof String) {
            String stringValue = (String) value;
            if ((stringValue.charAt(0) == '?') && (stringValue.charAt(stringValue.length()-1) == '?')) {
                String parameterName = stringValue.substring(1,stringValue.length()-1);
                value = parameterMap.get(parameterName);
            }
        }
        return value;
    }
    
    NoSqlPredicate convertPredicate(IPredicate predicate, String tableName, Map<String,Comparable<?>> parameterMap) {
        if (predicate == null)
            return null;
        NoSqlPredicate convertedPredicate = null;
        if (predicate instanceof CompoundPredicate) {
            CompoundPredicate compoundPredicate = (CompoundPredicate)predicate;
            ArrayList<NoSqlPredicate> noSqlPredicateList = new ArrayList<NoSqlPredicate>();
            for (IPredicate childPredicate: compoundPredicate.getPredicateList()) {
                boolean incorporated = false;
                if (childPredicate instanceof OperatorPredicate) {
                    OperatorPredicate childOperatorPredicate = (OperatorPredicate)childPredicate;
                    for (NoSqlPredicate childNoSqlPredicate: noSqlPredicateList) {
                        incorporated = childNoSqlPredicate.incorporateComparison(
                                childOperatorPredicate.getColumnName(), childOperatorPredicate.getOperator(),
                                getOperatorPredicateValue(childOperatorPredicate, parameterMap),
                                compoundPredicate.getOperator());
                        if (incorporated)
                            break;
                    }
                }
                if (!incorporated) {
                    NoSqlPredicate noSqlPredicate = convertPredicate(childPredicate, tableName, parameterMap);
                    noSqlPredicateList.add(noSqlPredicate);
                }
            }
            convertedPredicate = new NoSqlCompoundPredicate(this, compoundPredicate.getOperator(),
                    compoundPredicate.isNegated(), noSqlPredicateList);
        } else if (predicate instanceof OperatorPredicate) {
            OperatorPredicate operatorPredicate = (OperatorPredicate) predicate;
            Comparable<?> value = getOperatorPredicateValue(operatorPredicate, parameterMap);
            switch (operatorPredicate.getOperator()) {
            case EQ:
                convertedPredicate = new NoSqlRangePredicate(this, tableName,
                        operatorPredicate.getColumnName(), value, true, value, true);
                break;
            case LT:
                convertedPredicate = new NoSqlRangePredicate(this, tableName,
                        operatorPredicate.getColumnName(), null, false, value, false);
                break;
            case LTE:
                convertedPredicate = new NoSqlRangePredicate(this, tableName,
                        operatorPredicate.getColumnName(), null, false, value, true);
                break;
            case GT:
                convertedPredicate = new NoSqlRangePredicate(this, tableName,
                        operatorPredicate.getColumnName(), value, false, null, false);
                break;
            case GTE:
                convertedPredicate = new NoSqlRangePredicate(this, tableName,
                        operatorPredicate.getColumnName(), value, true, null, false);
                break;
            default:
                convertedPredicate = new NoSqlOperatorPredicate(this, operatorPredicate.getColumnName(),
                        operatorPredicate.getOperator(), value);
            }
        } else {
            throw new StorageException("Unknown predicate type");
        }
        
        return convertedPredicate;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    class RowComparator implements Comparator<Map<String,Object>> {
        private RowOrdering rowOrdering;
        
        public RowComparator(RowOrdering rowOrdering) {
            this.rowOrdering = rowOrdering;
        }
        
        public int compare(Map<String,Object> row1, Map<String,Object> row2) {
            if (rowOrdering == null)
                return 0;
            
            for (RowOrdering.Item item: rowOrdering.getItemList()) {
                Comparable key1 = (Comparable)row1.get(item.getColumn());
                Comparable key2 = (Comparable)row2.get(item.getColumn());
                int result = key1.compareTo(key2);
                if (result != 0) {
                    if (item.getDirection() == RowOrdering.Direction.DESCENDING)
                        result = -result;
                    return result;
                }
            }
            
            return 0;
        }
        
        public boolean equals(Object obj) {
            if (!(obj instanceof RowComparator))
                return false;
            RowComparator rc = (RowComparator)obj;
            if (rc.rowOrdering == null)
                return this.rowOrdering == null;
            return rc.rowOrdering.equals(this.rowOrdering);
        }
    }
    
    private NoSqlResultSet executeParameterizedQuery(String tableName, String[] columnNameList,
            IPredicate predicate, RowOrdering rowOrdering, Map<String,Comparable<?>> parameterMap) {
        NoSqlPredicate noSqlPredicate = convertPredicate(predicate, tableName, parameterMap);
        List<Map<String,Object>> rowList;
        if ((noSqlPredicate != null) && noSqlPredicate.canExecuteEfficiently()) {
            rowList = noSqlPredicate.execute(columnNameList);
        } else {
            rowList = new ArrayList<Map<String,Object>>();
            Collection<Map<String,Object>> allRowList = getAllRows(tableName, columnNameList);
            for (Map<String,Object> row: allRowList) {
                if ((noSqlPredicate == null) || noSqlPredicate.matchesRow(row)) {
                    rowList.add(row);
                }
            }
        }
        if (rowOrdering != null)
            Collections.sort(rowList, new RowComparator(rowOrdering));
            
        return new NoSqlResultSet(this, tableName, rowList);
    }
    
    @Override
    public IQuery createQuery(String tableName, String[] columnNameList,
            IPredicate predicate, RowOrdering rowOrdering) {
        return new NoSqlQuery(tableName, columnNameList, predicate, rowOrdering);
    }

    @Override
    public IResultSet executeQueryImpl(IQuery query) {
        NoSqlQuery noSqlQuery = (NoSqlQuery) query;
        return executeParameterizedQuery(noSqlQuery.getTableName(),
                noSqlQuery.getColumnNameList(), noSqlQuery.getPredicate(),
                noSqlQuery.getRowOrdering(), noSqlQuery.getParameterMap());
    }

    protected void sendNotification(String tableName, StorageSourceNotification.Action action,
            List<Map<String,Object>> rows) {
        Set<Object> rowKeys = new HashSet<Object>();
        String primaryKeyName = getTablePrimaryKeyName(tableName);
        for (Map<String,Object> row : rows) {
            Object rowKey = row.get(primaryKeyName);
            rowKeys.add(rowKey);
        }
        StorageSourceNotification notification =
            new StorageSourceNotification(tableName, action, rowKeys);
        notifyListeners(notification);
    }
    
    protected void sendNotification(String tableName,
            StorageSourceNotification.Action action, Set<Object> rowKeys) {
        StorageSourceNotification notification =
            new StorageSourceNotification(tableName, action, rowKeys);
        notifyListeners(notification);
    }
    
    protected void insertRowsAndNotify(String tableName, List<Map<String,Object>> insertRowList) {
        insertRows(tableName, insertRowList);
        sendNotification(tableName, StorageSourceNotification.Action.MODIFY, insertRowList);
    }

    @Override
    public void insertRowImpl(String tableName, Map<String, Object> values) {
        ArrayList<Map<String,Object>> rowList = new ArrayList<Map<String,Object>>();
        rowList.add(values);
        insertRowsAndNotify(tableName, rowList);
    }

    protected void updateRowsAndNotify(String tableName, Set<Object> rowKeys, Map<String,Object> updateRowList) {
        updateRows(tableName, rowKeys, updateRowList);
        sendNotification(tableName, StorageSourceNotification.Action.MODIFY, rowKeys);
    }

    protected void updateRowsAndNotify(String tableName, List<Map<String,Object>> updateRowList) {
        updateRows(tableName, updateRowList);
        sendNotification(tableName, StorageSourceNotification.Action.MODIFY, updateRowList);
    }

    @Override
    public void updateMatchingRowsImpl(String tableName, IPredicate predicate, Map<String,Object> values) {
        String primaryKeyName = getTablePrimaryKeyName(tableName);
        String[] columnNameList = {primaryKeyName};
        IResultSet resultSet = executeQuery(tableName, columnNameList, predicate, null);
        Set<Object> rowKeys = new HashSet<Object>();
        while (resultSet.next()) {
            String rowKey = resultSet.getString(primaryKeyName);
            rowKeys.add(rowKey);
        }
        updateRowsAndNotify(tableName, rowKeys, values);
    }
    
    @Override
    public void updateRowImpl(String tableName, Object rowKey, Map<String,Object> values) {
        Map<String,Object> valuesWithKey = new HashMap<String,Object>(values);
        String primaryKeyName = getTablePrimaryKeyName(tableName);
        valuesWithKey.put(primaryKeyName, rowKey);
        List<Map<String,Object>> rowList = new ArrayList<Map<String,Object>>();
        rowList.add(valuesWithKey);
        updateRowsAndNotify(tableName, rowList);
    }

   @Override
    public void updateRowImpl(String tableName, Map<String,Object> values) {
        List<Map<String,Object>> rowKeys = new ArrayList<Map<String,Object>>();
        rowKeys.add(values);
        updateRowsAndNotify(tableName, rowKeys);
    }

   protected void deleteRowsAndNotify(String tableName, Set<Object> rowKeyList) {
       deleteRows(tableName, rowKeyList);
       sendNotification(tableName, StorageSourceNotification.Action.DELETE, rowKeyList);
   }

    @Override
    public void deleteRowImpl(String tableName, Object key) {
        HashSet<Object> keys = new HashSet<Object>();
        keys.add(key);
        deleteRowsAndNotify(tableName, keys);
    }

    @Override
    public IResultSet getRowImpl(String tableName, Object rowKey) {
        List<Map<String,Object>> rowList = new ArrayList<Map<String,Object>>();
        Map<String,Object> row = getRow(tableName, null, rowKey);
        if (row != null)
            rowList.add(row);
        NoSqlResultSet resultSet = new NoSqlResultSet(this, tableName, rowList);
        return resultSet;
    }
   
    // Below are the methods that must be implemented by the subclasses
    
    protected abstract Collection<Map<String,Object>> getAllRows(String tableName, String[] columnNameList);
    
    protected abstract Map<String,Object> getRow(String tableName, String[] columnNameList, Object rowKey);
    
    protected abstract List<Map<String,Object>> executeEqualityQuery(String tableName,
            String[] columnNameList, String predicateColumnName, Comparable<?> value);
    
    protected abstract List<Map<String,Object>> executeRangeQuery(String tableName,
            String[] columnNameList, String predicateColumnName,
            Comparable<?> startValue, boolean startInclusive, Comparable<?> endValue, boolean endInclusive);
    
    protected abstract void insertRows(String tableName, List<Map<String,Object>> insertRowList);
    
    protected abstract void updateRows(String tableName, Set<Object> rowKeys, Map<String,Object> updateColumnMap);
}
