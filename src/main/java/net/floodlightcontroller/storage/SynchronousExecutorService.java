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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SynchronousExecutorService implements ExecutorService {

    class SynchronousFuture<T> implements Future<T> {

        T result;
        Exception exc;
        
        public SynchronousFuture() {
        }
        
        public SynchronousFuture(T result) {
            this.result = result;
        }
        
        public SynchronousFuture(Exception exc) {
            this.exc = exc;
        }
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            if (exc != null)
                throw new ExecutionException(exc);
            return result;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException {
            return get();
        }
    }
    
    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
        return null;
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return false;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        List<Future<T>> l = new ArrayList<Future<T>>();
        for (Callable<T> task : tasks) {
            Future<T> future = submit(task);
            l.add(future);
        }
        return l;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit units)
            throws InterruptedException {
        return invokeAll(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        for (Callable<T> task : tasks) {
            try {
                task.call();
            } catch (Exception e) {

            }
        }
        throw new ExecutionException(new Exception("no task completed successfully"));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit units) throws InterruptedException, ExecutionException,
            TimeoutException {
        return invokeAny(tasks);
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        try {
            T result = callable.call();
            return new SynchronousFuture<T>(result);
        }
        catch (Exception exc) {
            return new SynchronousFuture<T>(exc);
        }
    }
    
    @Override
    public Future<?> submit(Runnable runnable) {
        try {
            runnable.run();
            return new SynchronousFuture<Void>();
        }
        catch (Exception exc) {
            return new SynchronousFuture<Void>(exc);
        }
    }
    
    @Override
    public <T> Future<T> submit(Runnable runnable, T result) {
        try {
            runnable.run();
            return new SynchronousFuture<T>(result);
        }
        catch (Exception exc) {
            return new SynchronousFuture<T>(exc);
        }
    }
    
    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
