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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractStorageSource implements IStorageSource {
    protected static Logger logger = LoggerFactory.getLogger(AbstractStorageSource.class);

    // Shared instance of the executor to use to execute the storage tasks.
    // We make this a single threaded executor, because if we used a thread pool
    // then storage operations could be executed out of order which would cause
    // problems in some cases (e.g. delete and update of a row getting reordered).
    // If we wanted to make this more multi-threaded we could have multiple
    // worker threads/executors with affinity of operations on a given table
    // to a single worker thread. But for now, we'll keep it simple and just have
    // a single thread for all operations.
    protected static ExecutorService defaultExecutorService = Executors.newSingleThreadExecutor();
    
    protected ExecutorService executorService = defaultExecutorService;
    protected IStorageExceptionHandler exceptionHandler;

    private Map<String, Set<IStorageSourceListener>> listeners =
        new ConcurrentHashMap<String, Set<IStorageSourceListener>>();

    abstract class StorageCallable<V> implements Callable<V> {
        public V call() {
            try {
                return doStorageOperation();
            }
            catch (StorageException e) {
                logger.error("Failure in asynchronous call to executeQuery", e);
                if (exceptionHandler != null)
                    exceptionHandler.handleException(e);
                throw e;
            }
        }
        abstract protected V doStorageOperation();
    }
    
    abstract class StorageRunnable implements Runnable {
        public void run() {
            try {
                doStorageOperation();
            }
            catch (StorageException e) {
                logger.error("Failure in asynchronous call to updateRows", e);
                if (exceptionHandler != null)
                    exceptionHandler.handleException(e);
                throw e;
            }
        }
        abstract void doStorageOperation();
    }
    
    public AbstractStorageSource() {
        this.executorService = defaultExecutorService;
    }

    public AbstractStorageSource(ExecutorService executorService,
            IStorageExceptionHandler exceptionHandler) {
        setExecutorService(executorService);
        this.exceptionHandler = exceptionHandler;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = (executorService != null) ?
                executorService : defaultExecutorService;
    }
    
    @Override
    public void setExceptionHandler(IStorageExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
    
    @Override
    public abstract void setTablePrimaryKeyName(String tableName, String primaryKeyName);

    @Override
    public abstract void createTable(String tableName, Set<String> indexedColumns);

    @Override
    public abstract IQuery createQuery(String tableName, String[] columnNames,
            IPredicate predicate, RowOrdering ordering);

    @Override
    public abstract IResultSet executeQuery(IQuery query);

    @Override
    public IResultSet executeQuery(String tableName, String[] columnNames,
            IPredicate predicate, RowOrdering ordering) {
        IQuery query = createQuery(tableName, columnNames, predicate, ordering);
        IResultSet resultSet = executeQuery(query);
        return resultSet;
    }

    @Override
    public Object[] executeQuery(String tableName, String[] columnNames,
            IPredicate predicate, RowOrdering ordering, IRowMapper rowMapper) {
        List<Object> objectList = new ArrayList<Object>();
        IResultSet resultSet = executeQuery(tableName, columnNames, predicate, ordering);
        while (resultSet.next()) {
            Object object = rowMapper.mapRow(resultSet);
            objectList.add(object);
        }
        return objectList.toArray();
    }
    
    @Override
    public Future<IResultSet> executeQueryAsync(final IQuery query) {
        Future<IResultSet> future = executorService.submit(
            new StorageCallable<IResultSet>() {
                public IResultSet doStorageOperation() {
                    return executeQuery(query);
                }
            });
        return future;
    }

    @Override
    public Future<IResultSet> executeQueryAsync(final String tableName,
            final String[] columnNames,  final IPredicate predicate,
            final RowOrdering ordering) {
        Future<IResultSet> future = executorService.submit(
            new StorageCallable<IResultSet>() {
                public IResultSet doStorageOperation() {
                    return executeQuery(tableName, columnNames,
                            predicate, ordering);
                }
            });
        return future;
    }

    @Override
    public Future<Object[]> executeQueryAsync(final String tableName,
            final String[] columnNames,  final IPredicate predicate,
            final RowOrdering ordering, final IRowMapper rowMapper) {
        Future<Object[]> future = executorService.submit(
            new StorageCallable<Object[]>() {
                public Object[] doStorageOperation() {
                    return executeQuery(tableName, columnNames, predicate,
                            ordering, rowMapper);
                }
            });
        return future;
    }

    @Override
    public Future<?> insertRowAsync(final String tableName,
            final Map<String,Object> values) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    insertRow(tableName, values);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> updateRowsAsync(final String tableName,
            final IPredicate predicate, final Map<String,Object> values) {
        Future<?> future = executorService.submit(    
            new StorageRunnable() {
                public void doStorageOperation() {
                    updateRows(tableName, predicate, values);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> updateRowAsync(final String tableName,
            final Object rowKey, final Map<String,Object> values) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    updateRow(tableName, rowKey, values);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> updateRowAsync(final String tableName,
            final Map<String,Object> values) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    updateRow(tableName, values);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> deleteRowAsync(final String tableName, final Object rowKey) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    deleteRow(tableName, rowKey);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> deleteRowsAsync(final String tableName, final IPredicate predicate) {
        Future<?> future = executorService.submit(
                new StorageRunnable() {
                    public void doStorageOperation() {
                        deleteRows(tableName, predicate);
                    }
                }, null);
        return future;
    }

    @Override
    public Future<?> getRowAsync(final String tableName, final Object rowKey) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    getRow(tableName, rowKey);
                }
            }, null);
        return future;
    }
    
    @Override
    public Future<?> saveAsync(final IResultSet resultSet) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    resultSet.save();
                }
            }, null);
        return future;
    }

    @Override
    public abstract void insertRow(String tableName, Map<String, Object> values);

    @Override
    public abstract void updateRows(String tableName, IPredicate predicate,
            Map<String, Object> values);

    @Override
    public abstract void updateRow(String tableName, Object rowKey,
            Map<String, Object> values);

    @Override
    public abstract void updateRow(String tableName, Map<String, Object> values);

    @Override
    public abstract void deleteRow(String tableName, Object rowKey);

    @Override
    public abstract IResultSet getRow(String tableName, Object rowKey);

    @Override
    public synchronized void addListener(String tableName, IStorageSourceListener listener) {
        Set<IStorageSourceListener> tableListeners = listeners.get(tableName);
        if (tableListeners == null) {
            tableListeners = new CopyOnWriteArraySet<IStorageSourceListener>();
            listeners.put(tableName, tableListeners);
        }
        tableListeners.add(listener);
    }
  
    @Override
    public synchronized void removeListener(String tableName, IStorageSourceListener listener) {
        Set<IStorageSourceListener> tableListeners = listeners.get(tableName);
        if (tableListeners != null) {
            tableListeners.remove(listener);
        }
    }

    protected synchronized void notifyListeners(StorageSourceNotification notification) {
        String tableName = notification.getTableName();
        Set<Object> keys = notification.getKeys();
        Set<IStorageSourceListener> tableListeners = listeners.get(tableName);
        if (tableListeners != null) {
            for (IStorageSourceListener listener : tableListeners) {
                try {
                    switch (notification.getAction()) {
                        case MODIFY:
                            listener.rowsModified(tableName, keys);
                            break;
                        case DELETE:
                            listener.rowsDeleted(tableName, keys);
                            break;
                    }
                }
                catch (Exception e) {
                    logger.error("Exception caught handling storage notification", e);
                }
            }
        }
    }
    
    @Override
    public void notifyListeners(List<StorageSourceNotification> notifications) {
        for (StorageSourceNotification notification : notifications)
            notifyListeners(notification);
    }
    
}
