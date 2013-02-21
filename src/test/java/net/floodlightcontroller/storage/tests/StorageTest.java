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

package net.floodlightcontroller.storage.tests;

import static org.easymock.EasyMock.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.storage.CompoundPredicate;
import net.floodlightcontroller.storage.IStorageExceptionHandler;
import net.floodlightcontroller.storage.IPredicate;
import net.floodlightcontroller.storage.IQuery;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.IRowMapper;
import net.floodlightcontroller.storage.IStorageSourceListener;
import net.floodlightcontroller.storage.NullValueStorageException;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.RowOrdering;
import net.floodlightcontroller.storage.nosql.NoSqlStorageSource;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Test;

public abstract class StorageTest extends FloodlightTestCase {
    
    protected NoSqlStorageSource storageSource;
    protected RestApiServer restApi;
    
    protected String PERSON_TABLE_NAME = "Person";
    
    protected String PERSON_SSN = "SSN";
    protected String PERSON_FIRST_NAME = "FirstName";
    protected String PERSON_LAST_NAME = "LastName";
    protected String PERSON_AGE = "Age";
    protected String PERSON_REGISTERED = "Registered";
    
    protected String[] PERSON_COLUMN_LIST = {PERSON_SSN, PERSON_FIRST_NAME, PERSON_LAST_NAME, PERSON_AGE, PERSON_REGISTERED};
    
    class Person {
        private String ssn;
        private String firstName;
        private String lastName;
        int age;
        boolean registered;
        
        public Person(String ssn, String firstName, String lastName, int age, boolean registered) {
            this.ssn = ssn;
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
            this.registered = registered;
        }
        
        public String getSSN() {
            return ssn;
        }
        
        public String getFirstName() {
            return firstName;
        }
        
        public String getLastName() {
            return lastName;
            
        }
        
        public int getAge() {
            return age;
        }
        
        public boolean isRegistered() {
            return registered;
        }
    }
    
    class PersonRowMapper implements IRowMapper {
        public Object mapRow(IResultSet resultSet) {
            String ssn = resultSet.getString(PERSON_SSN);
            String firstName = resultSet.getString(PERSON_FIRST_NAME);
            String lastName = resultSet.getString(PERSON_LAST_NAME);
            int age = resultSet.getInt(PERSON_AGE);
            boolean registered = resultSet.getBoolean(PERSON_REGISTERED);
            return new Person(ssn, firstName, lastName, age, registered);
        }
    }
    
    Object[][] PERSON_INIT_DATA = {
            {"111-11-1111", "John", "Smith", 40, true},
            {"222-22-2222", "Jim", "White", 24, false},
            {"333-33-3333", "Lisa", "Jones", 27, true},
            {"444-44-4444", "Susan", "Jones", 14, false},
            {"555-55-5555", "Jose", "Garcia", 31, true},
            {"666-66-6666", "Abigail", "Johnson", 35, false},
            {"777-77-7777", "Bjorn", "Borg", 55, true},
            {"888-88-8888", "John", "McEnroe", 53, false}
    };

    Map<String,Object> createPersonRowValues(Object[] personData) {
        Map<String,Object> rowValues = new HashMap<String,Object>();
        for (int i = 0; i < PERSON_COLUMN_LIST.length; i++) {
            rowValues.put(PERSON_COLUMN_LIST[i], personData[i]);
        }
        return rowValues;
    }
    
    public void insertPerson(Object[] personData) {
        Map<String,Object> rowValues = createPersonRowValues(personData);
        storageSource.insertRow(PERSON_TABLE_NAME, rowValues);
    }
    
    public void initPersons() {
        for (Object[] row: PERSON_INIT_DATA) {
            insertPerson(row);
        }
    }
    
    public void setUp() throws Exception {
        super.setUp();
        Set<String> indexedColumnNames = new HashSet<String>();
        indexedColumnNames.add(PERSON_FIRST_NAME);
        indexedColumnNames.add(PERSON_LAST_NAME);
        storageSource.setExceptionHandler(null);
        storageSource.createTable(PERSON_TABLE_NAME, indexedColumnNames);
        storageSource.setTablePrimaryKeyName(PERSON_TABLE_NAME, PERSON_SSN);        
        initPersons();
    }

    public void checkExpectedResults(IResultSet resultSet, String[] columnNameList, Object[][] expectedRowList) {
        boolean nextResult;
        for (Object[] expectedRow: expectedRowList) {
            nextResult = resultSet.next();
            assertEquals(nextResult,true);
            assertEquals(expectedRow.length, columnNameList.length);
            for (int i = 0; i < expectedRow.length; i++) {
                Object expectedObject = expectedRow[i];
                String columnName = columnNameList[i];
                if (expectedObject instanceof Boolean)
                    assertEquals(((Boolean)expectedObject).booleanValue(), resultSet.getBoolean(columnName));
                else if (expectedObject instanceof Byte)
                    assertEquals(((Byte)expectedObject).byteValue(), resultSet.getByte(columnName));
                else if (expectedObject instanceof Short)
                    assertEquals(((Short)expectedObject).shortValue(), resultSet.getShort(columnName));
                else if (expectedObject instanceof Integer)
                    assertEquals(((Integer)expectedObject).intValue(), resultSet.getInt(columnName));
                else if (expectedObject instanceof Long)
                    assertEquals(((Long)expectedObject).longValue(), resultSet.getLong(columnName));
                else if (expectedObject instanceof Float)
                    assertEquals(((Float)expectedObject).floatValue(), resultSet.getFloat(columnName), 0.00001);
                else if (expectedObject instanceof Double)
                    assertEquals(((Double)expectedObject).doubleValue(), resultSet.getDouble(columnName), 0.00001);
                else if (expectedObject instanceof byte[])
                    assertTrue(Arrays.equals((byte[])expectedObject, resultSet.getByteArray(columnName)));
                else if (expectedObject instanceof String)
                    assertEquals((String)expectedObject, resultSet.getString(columnName));
                else
                    assertTrue("Unexpected column value type", false);
            }
        }
        nextResult = resultSet.next();
        assertEquals(nextResult,false);
        resultSet.close();
    }
    
    @Test
    public void testInsertRows() {
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, null, new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, PERSON_COLUMN_LIST, PERSON_INIT_DATA);
    }
    
    @Test
    public void testOperatorQuery() {
        Object[][] expectedResults = {
                {"John", "Smith", 40},
                {"Jim", "White", 24},
        };
        String[] columnList = {PERSON_FIRST_NAME,PERSON_LAST_NAME,PERSON_AGE};
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, columnList,
                new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.GTE, "Sm"),
                new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, columnList, expectedResults);
    }
    
    @Test
    public void testAndQuery() {
        String[] columnList = {PERSON_FIRST_NAME,PERSON_LAST_NAME};        
        Object[][] expectedResults = {
                {"Lisa", "Jones"},
                {"Susan", "Jones"},
                {"Jose", "Garcia"},
                {"Abigail", "Johnson"},
                {"John", "McEnroe"}
        };
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, columnList,
                new CompoundPredicate(CompoundPredicate.Operator.AND, false,
                        new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.GTE, "G"),
                        new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.LT, "N")
                ),
                new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, columnList, expectedResults);
    }
    
    @Test
    public void testEfficientOrQuery() {
        String[] columnList = {PERSON_FIRST_NAME,PERSON_LAST_NAME};
        Object[][] expectedResults = {
                {"John", "Smith"},
                {"Lisa", "Jones"},
                {"Susan", "Jones"}
        };
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, columnList,
                new CompoundPredicate(CompoundPredicate.Operator.OR, false,
                        new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.EQ, "Jones"),
                        new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.EQ, "Smith")
                ),
                new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, columnList, expectedResults);
    }

    @Test
    public void testEfficientAndQuery() {
        String[] columnList = {PERSON_FIRST_NAME,PERSON_LAST_NAME};
        Object[][] expectedResults = {
                {"Lisa", "Jones"}
        };
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, columnList,
                new CompoundPredicate(CompoundPredicate.Operator.AND, false,
                        new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.EQ, "Jones"),
                        new OperatorPredicate(PERSON_FIRST_NAME, OperatorPredicate.Operator.EQ, "Lisa")
                ),
                new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, columnList, expectedResults);
    }

    @Test
    public void testOrQuery() {
        String[] columnList = {PERSON_FIRST_NAME,PERSON_LAST_NAME, PERSON_AGE};        
        Object[][] expectedResults = {
                {"John", "Smith", 40},
                {"Lisa", "Jones", 27},
                {"Abigail", "Johnson", 35},
                {"Bjorn", "Borg", 55},
                {"John", "McEnroe", 53}
        };
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, columnList,
                new CompoundPredicate(CompoundPredicate.Operator.OR, false,
                        new OperatorPredicate(PERSON_AGE, OperatorPredicate.Operator.GTE, 35),
                        new OperatorPredicate(PERSON_FIRST_NAME, OperatorPredicate.Operator.EQ, "Lisa")
                ),
                new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, columnList, expectedResults);
}
    
    @Test
    public void testCreateQuery() {
        String[] columnList = {PERSON_FIRST_NAME,PERSON_LAST_NAME};
        Object[][] expectedResults = {
                {"Lisa", "Jones"},
                {"Susan", "Jones"}
        };
        IPredicate predicate = new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.EQ, "Jones");
        IQuery query = storageSource.createQuery(PERSON_TABLE_NAME, columnList, predicate, new RowOrdering(PERSON_SSN));
        IResultSet resultSet = storageSource.executeQuery(query);
        checkExpectedResults(resultSet, columnList, expectedResults);
    }
    
    @Test
    public void testQueryParameters() {
        String[] columnList = {PERSON_FIRST_NAME,PERSON_LAST_NAME, PERSON_AGE};        
        Object[][] expectedResults = {
                {"John", "Smith", 40},
                {"Bjorn", "Borg", 55},
                {"John", "McEnroe", 53}
        };
        IPredicate predicate = new OperatorPredicate(PERSON_AGE, OperatorPredicate.Operator.GTE, "?MinimumAge?");
        IQuery query = storageSource.createQuery(PERSON_TABLE_NAME, columnList, predicate, new RowOrdering(PERSON_SSN));
        query.setParameter("MinimumAge", 40);
        IResultSet resultSet = storageSource.executeQuery(query);
        checkExpectedResults(resultSet, columnList, expectedResults);
    }
    
    private void checkPerson(Person person, Object[] expectedValues) {
        assertEquals(person.getSSN(), expectedValues[0]);
        assertEquals(person.getFirstName(), expectedValues[1]);
        assertEquals(person.getLastName(), expectedValues[2]);
        assertEquals(person.getAge(), expectedValues[3]);
        assertEquals(person.isRegistered(), expectedValues[4]);
    }
    
    @Test
    public void testRowMapper() {
        Object[][] expectedResults = {
                PERSON_INIT_DATA[2],
                PERSON_INIT_DATA[3]
        };
        IPredicate predicate = new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.EQ, "Jones");
        IRowMapper rowMapper = new PersonRowMapper();
        Object[] personList = storageSource.executeQuery(PERSON_TABLE_NAME, null, predicate, new RowOrdering(PERSON_SSN), rowMapper);
        assertEquals(personList.length, 2);
        for (int i = 0; i < personList.length; i++)
            checkPerson((Person)personList[i], expectedResults[i]);
    }
    
    @Test
    public void testDeleteRowsDirect() {
        
        storageSource.deleteRow(PERSON_TABLE_NAME, "111-11-1111");
        storageSource.deleteRow(PERSON_TABLE_NAME, "222-22-2222");
        storageSource.deleteRow(PERSON_TABLE_NAME, "333-33-3333");
        storageSource.deleteRow(PERSON_TABLE_NAME, "444-44-4444");
        
        Object[][] expectedResults = {
                {"555-55-5555", "Jose", "Garcia", 31, true},
                {"666-66-6666", "Abigail", "Johnson", 35, false},
                {"777-77-7777", "Bjorn", "Borg", 55, true},
                {"888-88-8888", "John", "McEnroe", 53, false}
        };
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, PERSON_COLUMN_LIST, null, new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedResults);
    }
    
    @Test
    public void testDeleteRowsFromResultSet() {
        Object[][] expectedResults = {
                {"555-55-5555", "Jose", "Garcia", 31, true},
                {"666-66-6666", "Abigail", "Johnson", 35, false},
                {"777-77-7777", "Bjorn", "Borg", 55, true},
                {"888-88-8888", "John", "McEnroe", 53, false}
        };
        
        // Query once to delete the rows
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, PERSON_COLUMN_LIST, null, new RowOrdering(PERSON_SSN));
        for (int i = 0; i < 4; i++) {
            resultSet.next();
            resultSet.deleteRow();
        }
        resultSet.save();
        resultSet.close();
        
        // Now query again to verify that the rows were deleted
        resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, PERSON_COLUMN_LIST, null, new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedResults);
    }
    
    @Test
    public void testDeleteMatchingRows() {
        Object[][] expectedResults = {
                {"111-11-1111", "John", "Smith", 40, true},
                {"777-77-7777", "Bjorn", "Borg", 55, true},
                {"888-88-8888", "John", "McEnroe", 53, false}
        };
        storageSource.deleteMatchingRows(PERSON_TABLE_NAME, new OperatorPredicate(PERSON_AGE, OperatorPredicate.Operator.LT, 40));
        
        // Now query again to verify that the rows were deleted
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, PERSON_COLUMN_LIST, null, new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedResults);
        
        storageSource.deleteMatchingRows(PERSON_TABLE_NAME, null);

        // Now query again to verify that all rows were deleted
        resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, PERSON_COLUMN_LIST, null, new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, PERSON_COLUMN_LIST, new Object[0][]);
    }
    
    @Test
    public void testUpdateRowsDirect() {
        
        Object[][] expectedResults = {
                {"777-77-7777", "Tennis", "Borg", 60, true},
                {"888-88-8888", "Tennis", "McEnroe", 60, false}
        };
        Map<String,Object> updateValues = new HashMap<String,Object>();
        updateValues.put(PERSON_FIRST_NAME, "Tennis");
        updateValues.put(PERSON_AGE, 60);
        
        IPredicate predicate = new OperatorPredicate(PERSON_AGE, OperatorPredicate.Operator.GT, 50);
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, predicate, new RowOrdering(PERSON_SSN));
        while (resultSet.next()) {
            String key = resultSet.getString(PERSON_SSN);
            storageSource.updateRow(PERSON_TABLE_NAME, key, updateValues);
        }
        resultSet.close();
        
        resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, PERSON_COLUMN_LIST, predicate, new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedResults);
    }
    
    @Test
    public void testUpdateRowsFromResultSet() {
        
        Object[][] expectedResults = {
                {"777-77-7777", "Tennis", "Borg", 60, true},
                {"888-88-8888", "Tennis", "McEnroe", 60, false}
        };
        
        IPredicate predicate = new OperatorPredicate(PERSON_AGE, OperatorPredicate.Operator.GT, 50);
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, predicate, null);
        while (resultSet.next()) {
            resultSet.setString(PERSON_FIRST_NAME, "Tennis");
            resultSet.setInt(PERSON_AGE, 60);
        }
        resultSet.save();
        resultSet.close();
        
        resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, PERSON_COLUMN_LIST, predicate, new RowOrdering(PERSON_SSN));
        checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedResults);
    }
    
    @Test
    public void testNullValues() {
        
        IPredicate predicate = new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.EQ, "Jones");
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, predicate, new RowOrdering(PERSON_SSN));
        while (resultSet.next()) {
            resultSet.setNull(PERSON_FIRST_NAME);
            resultSet.setIntegerObject(PERSON_AGE, null);
        }
        resultSet.save();
        resultSet.close();

        resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, predicate, new RowOrdering(PERSON_SSN));
        int count = 0;
        while (resultSet.next()) {
            boolean checkNull = resultSet.isNull(PERSON_FIRST_NAME);
            assertTrue(checkNull);
            String s = resultSet.getString(PERSON_FIRST_NAME);
            assertEquals(s, null);
            checkNull = resultSet.isNull(PERSON_AGE);
            assertTrue(checkNull);
            Integer intObj = resultSet.getIntegerObject(PERSON_AGE);
            assertEquals(intObj, null);
            Short shortObj = resultSet.getShortObject(PERSON_AGE);
            assertEquals(shortObj, null);
            boolean excThrown = false;
            try {
                resultSet.getInt(PERSON_AGE);
            }
            catch (NullValueStorageException exc) {
                excThrown = true;
            }
            assertTrue(excThrown);
            count++;
        }
        resultSet.close();
        assertEquals(count, 2);
        
        predicate = new OperatorPredicate(PERSON_FIRST_NAME, OperatorPredicate.Operator.EQ, null);
        resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, predicate, new RowOrdering(PERSON_SSN));
        count = 0;
        while (resultSet.next()) {
            boolean checkNull = resultSet.isNull(PERSON_FIRST_NAME);
            assertTrue(checkNull);
            count++;
            checkNull = resultSet.isNull(PERSON_AGE);
            assertTrue(checkNull);
        }
        resultSet.close();
        assertEquals(count, 2);
    }
    
    @Test
    public void testInsertNotification() {
        // Set up the listener and record the expected notification
        IStorageSourceListener mockListener = createNiceMock(IStorageSourceListener.class);
        Set<Object> expectedKeys = new HashSet<Object>();
        expectedKeys.add("999-99-9999");
        mockListener.rowsModified(PERSON_TABLE_NAME, expectedKeys);
        
        replay(mockListener);

        // Now try it for real
        storageSource.addListener(PERSON_TABLE_NAME, mockListener);

        // Create a new person, which should trigger the listener
        Object[] newPerson = {"999-99-9999", "Serena", "Williams", 22, true};
        insertPerson(newPerson);
        
        verify(mockListener);
    }
    
    @Test
    public void testUpdateNotification() {
        // Set up the listener and record the expected notification
        IStorageSourceListener mockListener = createNiceMock(IStorageSourceListener.class);
        Set<Object> expectedKeys = new HashSet<Object>();
        expectedKeys.add("111-11-1111");
        mockListener.rowsModified(PERSON_TABLE_NAME, expectedKeys);
        
        replay(mockListener);

        // Now try it for real
        storageSource.addListener(PERSON_TABLE_NAME, mockListener);

        // Create a new person, which should trigger the listener
        Map<String,Object> updateValues = new HashMap<String,Object>();
        updateValues.put(PERSON_FIRST_NAME, "Tennis");
        storageSource.updateRow(PERSON_TABLE_NAME, "111-11-1111", updateValues);
        
        verify(mockListener);
    }
    
    @Test
    public void testDeleteNotification() {
        IStorageSourceListener mockListener = createNiceMock(IStorageSourceListener.class);
        Set<Object> expectedKeys = new HashSet<Object>();
        expectedKeys.add("111-11-1111");
        mockListener.rowsDeleted(PERSON_TABLE_NAME, expectedKeys);
        
        replay(mockListener);

        // Now try it for real
        storageSource.addListener(PERSON_TABLE_NAME, mockListener);

        // Create a new person, which should trigger the listener
        storageSource.deleteRow(PERSON_TABLE_NAME, "111-11-1111");
        
        verify(mockListener);
    }
    
    public void waitForFuture(Future<?> future) {
        try
        {
            future.get(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException exc)
        {
            fail("Async storage operation interrupted");
        }
        catch (ExecutionException exc) {
            fail("Async storage operation failed");
        }
        catch (TimeoutException exc) {
            fail("Async storage operation timed out");
        }
    }
    
    @Test
    public void testAsyncQuery1() {
        Object[][] expectedResults = {
                {"John", "Smith", 40},
                {"Jim", "White", 24},
        };
        String[] columnList = {PERSON_FIRST_NAME,PERSON_LAST_NAME,PERSON_AGE};
        IPredicate predicate = new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.GTE, "Sm");
        IQuery query = storageSource.createQuery(PERSON_TABLE_NAME, columnList, predicate, new RowOrdering(PERSON_SSN));
        Future<IResultSet> future = storageSource.executeQueryAsync(query);
        waitForFuture(future);
        try {
            IResultSet resultSet = future.get();
            checkExpectedResults(resultSet, columnList, expectedResults);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
    }
    
    @Test
    public void testAsyncQuery2() {
        Object[][] expectedResults = {
                {"John", "Smith", 40},
                {"Jim", "White", 24},
        };
        String[] columnList = {PERSON_FIRST_NAME,PERSON_LAST_NAME,PERSON_AGE};
        IPredicate predicate = new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.GTE, "Sm");
        Future<IResultSet> future = storageSource.executeQueryAsync(PERSON_TABLE_NAME,
                columnList, predicate, new RowOrdering(PERSON_SSN));
        waitForFuture(future);
        try {
            IResultSet resultSet = future.get();
            checkExpectedResults(resultSet, columnList, expectedResults);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
    }
    
    @Test
    public void testAsyncQuery3() {
        Object[][] expectedResults = {
                PERSON_INIT_DATA[2],
                PERSON_INIT_DATA[3]
        };
        IPredicate predicate = new OperatorPredicate(PERSON_LAST_NAME, OperatorPredicate.Operator.EQ, "Jones");
        IRowMapper rowMapper = new PersonRowMapper();
        Future<Object[]> future = storageSource.executeQueryAsync(PERSON_TABLE_NAME,
                null, predicate, new RowOrdering(PERSON_SSN), rowMapper);
        waitForFuture(future);
        try {
            Object[] personList = future.get();
            assertEquals(personList.length, 2);
            for (int i = 0; i < personList.length; i++)
                checkPerson((Person)personList[i], expectedResults[i]);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
    }
    
    @Test
    public void testAsyncException() {
        class TestExceptionHandler implements IStorageExceptionHandler {
            public int exceptionCount = 0;
            @Override
            public void handleException(Exception exception) {
                exceptionCount++;
            }
        }
        TestExceptionHandler exceptionHandler = new TestExceptionHandler();
        storageSource.setExceptionHandler(exceptionHandler);
        
        // Use an invalid table name, which should cause the storage API call to throw
        // an exception, which should then be converted to an ExecutionException.
        Future<IResultSet> future = storageSource.executeQueryAsync("InvalidTableName",
                null, null, null);
        try {
            future.get(10, TimeUnit.SECONDS);
            fail("Expected ExecutionException was not thrown");
        }
        catch (ExecutionException e) {
            assertTrue(true);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
        assertEquals(exceptionHandler.exceptionCount, 1);
    }
    
    @Test
    public void testAsyncInsertRow() {
        Object[][] newPersonInfo = {{"999-99-9999", "Ellen", "Wilson", 40, true}};
        Map<String,Object> rowValues = createPersonRowValues(newPersonInfo[0]);
        Future<?> future = storageSource.insertRowAsync(PERSON_TABLE_NAME, rowValues);
        waitForFuture(future);
        try {
            IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, null, new RowOrdering(PERSON_SSN));
            Object[][] expectedPersons = Arrays.copyOf(PERSON_INIT_DATA, PERSON_INIT_DATA.length + newPersonInfo.length);
            System.arraycopy(newPersonInfo, 0, expectedPersons, PERSON_INIT_DATA.length, newPersonInfo.length);
            checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedPersons);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
    }
    
    @Test
    public void testAsyncUpdateRow() {
        Map<String,Object> updateValues = new HashMap<String,Object>();
        updateValues.put(PERSON_SSN, "777-77-7777");
        updateValues.put(PERSON_FIRST_NAME, "Tennis");
        updateValues.put(PERSON_AGE, 60);

        Future<?> future = storageSource.updateRowAsync(PERSON_TABLE_NAME, updateValues);
        waitForFuture(future);

        try {
            IResultSet resultSet = storageSource.getRow(PERSON_TABLE_NAME, "777-77-7777");
            Object[][] expectedPersons = {{"777-77-7777", "Tennis", "Borg", 60, true}};
            checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedPersons);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
    }
    
    @Test
    public void testAsyncUpdateRow2() {
        Map<String,Object> updateValues = new HashMap<String,Object>();
        updateValues.put(PERSON_FIRST_NAME, "Tennis");
        updateValues.put(PERSON_AGE, 60);

        Future<?> future = storageSource.updateRowAsync(PERSON_TABLE_NAME, "777-77-7777", updateValues);
        waitForFuture(future);

        try {
            IResultSet resultSet = storageSource.getRow(PERSON_TABLE_NAME, "777-77-7777");
            Object[][] expectedPersons = {{"777-77-7777", "Tennis", "Borg", 60, true}};
            checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedPersons);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
    }
    
    @Test
    public void testAsyncUpdateMatchingRows() {
        Map<String,Object> updateValues = new HashMap<String,Object>();
        updateValues.put(PERSON_FIRST_NAME, "Tennis");
        updateValues.put(PERSON_AGE, 60);

        IPredicate predicate = new OperatorPredicate(PERSON_SSN, OperatorPredicate.Operator.EQ, "777-77-7777");
        Future<?> future = storageSource.updateMatchingRowsAsync(PERSON_TABLE_NAME, predicate, updateValues);
        waitForFuture(future);
        try {
            IResultSet resultSet = storageSource.getRow(PERSON_TABLE_NAME, "777-77-7777");
            Object[][] expectedPersons = {{"777-77-7777", "Tennis", "Borg", 60, true}};
            checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedPersons);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
    }
    
    @Test
    public void testAsyncDeleteRow() {
        Future<?> future = storageSource.deleteRowAsync(PERSON_TABLE_NAME, "111-11-1111");
        waitForFuture(future);
        try {
            IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, null, new RowOrdering(PERSON_SSN));
            Object[][] expectedPersons = Arrays.copyOfRange(PERSON_INIT_DATA, 1, PERSON_INIT_DATA.length);
            checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedPersons);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
    }
    
    @Test
    public void testAsyncDeleteMatchingRows() {
        Future<?> future = storageSource.deleteMatchingRowsAsync(PERSON_TABLE_NAME, null);
        waitForFuture(future);
        try {
            IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, null, new RowOrdering(PERSON_SSN));
            checkExpectedResults(resultSet, PERSON_COLUMN_LIST, new Object[0][]);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
    }
    
    @Test
    public void testAsyncSave() {
        // Get a result set and make some changes to it
        IResultSet resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, null, new RowOrdering(PERSON_SSN));
        resultSet.next();
        resultSet.deleteRow();
        resultSet.next();
        resultSet.setString(PERSON_FIRST_NAME, "John");
        
        Future<?> future = storageSource.saveAsync(resultSet);
        waitForFuture(future);
        try {
            resultSet = storageSource.executeQuery(PERSON_TABLE_NAME, null, null, new RowOrdering(PERSON_SSN));
            Object[][] expectedPersons = Arrays.copyOfRange(PERSON_INIT_DATA, 1, PERSON_INIT_DATA.length);
            expectedPersons[0][1] = "John";
            checkExpectedResults(resultSet, PERSON_COLUMN_LIST, expectedPersons);
        }
        catch (Exception e) {
            fail("Exception thrown in async storage operation: " + e.toString());
        }
        
    }
}
