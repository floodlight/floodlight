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

package net.floodlightcontroller.core.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import net.floodlightcontroller.test.FloodlightTestCase;

public class SingletonTaskTest extends FloodlightTestCase {

    public int ran = 0;
    public int finished = 0;
    public long time = 0;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        ran = 0;
        finished = 0;
        time = 0;
    }

    @Test
    public void testBasic() throws InterruptedException {
        ScheduledExecutorService ses =
            Executors.newSingleThreadScheduledExecutor();

        SingletonTask st1 = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                ran += 1;
            }
        });
        st1.reschedule(0, null);
        ses.shutdown();
        ses.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals("Check that task ran", 1, ran);
    }

    @Test
    public void testDelay() throws InterruptedException {
        ScheduledExecutorService ses =
            Executors.newSingleThreadScheduledExecutor();

        SingletonTask st1 = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                ran += 1;
                time = System.nanoTime();
            }
        });
        long start = System.nanoTime();
        st1.reschedule(10, TimeUnit.MILLISECONDS);
        assertFalse("Check that task hasn't run yet", ran > 0);

        ses.shutdown();
        ses.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals("Check that task ran", 1, ran);
        assertTrue("Check that time passed appropriately",
                   (time - start) >= TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testReschedule() throws InterruptedException {
        ScheduledExecutorService ses =
            Executors.newSingleThreadScheduledExecutor();

        final Object tc = this;
        SingletonTask st1 = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                synchronized (tc) {
                    ran += 1;
                }
                time = System.nanoTime();
            }
        });
        long start = System.nanoTime();
        st1.reschedule(20, TimeUnit.MILLISECONDS);
        Thread.sleep(5);
        assertFalse("Check that task hasn't run yet", ran > 0);
        st1.reschedule(20, TimeUnit.MILLISECONDS);
        Thread.sleep(5);
        assertFalse("Check that task hasn't run yet", ran > 0);
        st1.reschedule(20, TimeUnit.MILLISECONDS);
        Thread.sleep(5);
        assertFalse("Check that task hasn't run yet", ran > 0);
        st1.reschedule(20, TimeUnit.MILLISECONDS);
        Thread.sleep(5);
        assertFalse("Check that task hasn't run yet", ran > 0);
        st1.reschedule(20, TimeUnit.MILLISECONDS);
        Thread.sleep(5);
        assertFalse("Check that task hasn't run yet", ran > 0);
        st1.reschedule(20, TimeUnit.MILLISECONDS);
        Thread.sleep(5);
        assertFalse("Check that task hasn't run yet", ran > 0);
        st1.reschedule(20, TimeUnit.MILLISECONDS);
        Thread.sleep(5);
        assertFalse("Check that task hasn't run yet", ran > 0);
        st1.reschedule(20, TimeUnit.MILLISECONDS);
        Thread.sleep(5);
        assertFalse("Check that task hasn't run yet", ran > 0);

        ses.shutdown();
        ses.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals("Check that task ran only once", 1, ran);
        assertTrue("Check that time passed appropriately: " + (time - start),
                (time - start) >= TimeUnit.NANOSECONDS.convert(55, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testConcurrentAddDelay() throws InterruptedException {
        ScheduledExecutorService ses =
            Executors.newSingleThreadScheduledExecutor();

        final Object tc = this;
        SingletonTask st1 = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                synchronized (tc) {
                    ran += 1;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                synchronized (tc) {
                    finished += 1;
                    time = System.nanoTime();
                }
            }
        });

        long start = System.nanoTime();
        st1.reschedule(5, TimeUnit.MILLISECONDS);
        Thread.sleep(20);
        assertEquals("Check that task started", 1, ran);
        assertEquals("Check that task not finished", 0, finished);
        st1.reschedule(75, TimeUnit.MILLISECONDS);
        assertTrue("Check task running state true", st1.context.taskRunning);
        assertTrue("Check task should run state true", st1.context.taskShouldRun);
        assertEquals("Check that task started", 1, ran);
        assertEquals("Check that task not finished", 0, finished);

        Thread.sleep(150);

        assertTrue("Check task running state false", !st1.context.taskRunning);
        assertTrue("Check task should run state false", !st1.context.taskShouldRun);
        assertEquals("Check that task ran exactly twice", 2, ran);
        assertEquals("Check that task finished exactly twice", 2, finished);

        assertTrue("Check that time passed appropriately: " + (time - start),
                (time - start) >= TimeUnit.NANOSECONDS.convert(130, TimeUnit.MILLISECONDS));
        assertTrue("Check that time passed appropriately: " + (time - start),
                (time - start) <= TimeUnit.NANOSECONDS.convert(160, TimeUnit.MILLISECONDS));

        ses.shutdown();
        ses.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testConcurrentAddDelay2() throws InterruptedException {
        ScheduledExecutorService ses =
            Executors.newSingleThreadScheduledExecutor();

        final Object tc = this;
        SingletonTask st1 = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                synchronized (tc) {
                    ran += 1;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                synchronized (tc) {
                    finished += 1;
                    time = System.nanoTime();
                }
            }
        });

        long start = System.nanoTime();
        st1.reschedule(5, TimeUnit.MILLISECONDS);
        Thread.sleep(20);
        assertEquals("Check that task started", 1, ran);
        assertEquals("Check that task not finished", 0, finished);
        st1.reschedule(25, TimeUnit.MILLISECONDS);
        assertTrue("Check task running state true", st1.context.taskRunning);
        assertTrue("Check task should run state true", st1.context.taskShouldRun);
        assertEquals("Check that task started", 1, ran);
        assertEquals("Check that task not finished", 0, finished);

        Thread.sleep(150);

        assertTrue("Check task running state false", !st1.context.taskRunning);
        assertTrue("Check task should run state false", !st1.context.taskShouldRun);
        assertEquals("Check that task ran exactly twice", 2, ran);
        assertEquals("Check that task finished exactly twice", 2, finished);

        assertTrue("Check that time passed appropriately: " + (time - start),
                (time - start) >= TimeUnit.NANOSECONDS.convert(100, TimeUnit.MILLISECONDS));
        assertTrue("Check that time passed appropriately: " + (time - start),
                (time - start) <= TimeUnit.NANOSECONDS.convert(125, TimeUnit.MILLISECONDS));

        ses.shutdown();
        ses.awaitTermination(5, TimeUnit.SECONDS);
    }


    @Test
    public void testConcurrentAddNoDelay() throws InterruptedException {
        ScheduledExecutorService ses =
            Executors.newSingleThreadScheduledExecutor();

        final Object tc = this;
        SingletonTask st1 = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                synchronized (tc) {
                    ran += 1;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                synchronized (tc) {
                    finished += 1;
                    time = System.nanoTime();
                }
            }
        });

        long start = System.nanoTime();
        st1.reschedule(0, null);
        Thread.sleep(20);
        assertEquals("Check that task started", 1, ran);
        assertEquals("Check that task not finished", 0, finished);
        st1.reschedule(0, null);
        assertTrue("Check task running state true", st1.context.taskRunning);
        assertTrue("Check task should run state true", st1.context.taskShouldRun);
        assertEquals("Check that task started", 1, ran);
        assertEquals("Check that task not finished", 0, finished);

        Thread.sleep(150);

        assertTrue("Check task running state false", !st1.context.taskRunning);
        assertTrue("Check task should run state false", !st1.context.taskShouldRun);
        assertEquals("Check that task ran exactly twice", 2, ran);
        assertEquals("Check that task finished exactly twice", 2, finished);

        assertTrue("Check that time passed appropriately: " + (time - start),
                (time - start) >= TimeUnit.NANOSECONDS.convert(90, TimeUnit.MILLISECONDS));
        assertTrue("Check that time passed appropriately: " + (time - start),
                (time - start) <= TimeUnit.NANOSECONDS.convert(130, TimeUnit.MILLISECONDS));

        ses.shutdown();
        ses.awaitTermination(5, TimeUnit.SECONDS);
    }
}
