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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.annotations.LogMessageDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This allows you to represent a task that should be queued for future execution
 * but where you only want the task to complete once in response to some sequence 
 * of events.  For example, if you get a change notification and want to reload state,
 * you only want to reload the state once, at the end, and don't want to queue
 * an update for every notification that might come in.
 * 
 * The semantics are as follows:
 * * If the task hasn't begun yet, do not queue a new task
 * * If the task has begun, set a bit to restart it after the current task finishes
 */
public class SingletonTask {
    protected static final Logger logger = 
            LoggerFactory.getLogger(SingletonTask.class);
            
    protected static class SingletonTaskContext  {
        protected boolean taskShouldRun = false;
        protected boolean taskRunning = false;

        protected SingletonTaskWorker waitingTask = null;
    }

    protected static class SingletonTaskWorker implements Runnable  {
        SingletonTask parent;
        boolean canceled = false;
        long nextschedule = 0;

        public SingletonTaskWorker(SingletonTask parent) {
            super();
            this.parent = parent;
        }

        @Override
        @LogMessageDoc(level="ERROR",
                       message="Exception while executing task",
                       recommendation=LogMessageDoc.GENERIC_ACTION)
        public void run() {
            synchronized (parent.context) {
                if (canceled || !parent.context.taskShouldRun)
                    return;

                parent.context.taskRunning = true;
                parent.context.taskShouldRun = false;
            }

            try {
                parent.task.run();
            } catch (Exception e) {
                logger.error("Exception while executing task", e);
            }
            catch (Error e) {
                logger.error("Error while executing task", e);
                throw e;
            }

            synchronized (parent.context) {
                parent.context.taskRunning = false;

                if (parent.context.taskShouldRun) {
                    long now = System.nanoTime();
                    if ((nextschedule <= 0 || (nextschedule - now) <= 0)) {
                        parent.ses.execute(this);
                    } else {
                        parent.ses.schedule(this, 
                                            nextschedule-now, 
                                            TimeUnit.NANOSECONDS);
                    }
                }
            }
        }
    }

    protected SingletonTaskContext context = new SingletonTaskContext();
    protected Runnable task;
    protected ScheduledExecutorService ses;


    /**
     * Construct a new SingletonTask for the given runnable.  The context
     * is used to manage the state of the task execution and can be shared
     * by more than one instance of the runnable.
     * @param context
     * @param Task
     */
    public SingletonTask(ScheduledExecutorService ses,
            Runnable task) {
        super();
        this.task = task;
        this.ses = ses;
    }

    /**
     * Schedule the task to run if there's not already a task scheduled
     * If there is such a task waiting that has not already started, it
     * cancel that task and reschedule it to run at the given time.  If the
     * task is already started, it will cause the task to be rescheduled once
     * it completes to run after delay from the time of reschedule.
     * 
     * @param delay the delay in scheduling
     * @param unit the timeunit of the delay
     */
    public void reschedule(long delay, TimeUnit unit) {
        boolean needQueue = true;
        SingletonTaskWorker stw = null;

        synchronized (context) {
            if (context.taskRunning || context.taskShouldRun) {
                if (context.taskRunning) {
                    // schedule to restart at the right time
                    if (delay > 0) {
                        long now = System.nanoTime();
                        long then = 
                            now + TimeUnit.NANOSECONDS.convert(delay, unit);
                        context.waitingTask.nextschedule = then;
//                        logger.debug("rescheduled task " + this + " for " + TimeUnit.SECONDS.convert(then, TimeUnit.NANOSECONDS) + "s. A bunch of these messages -may- indicate you have a blocked task.");
                    } else {
                        context.waitingTask.nextschedule = 0;
                    }
                    needQueue = false;
                } else {
                    // cancel and requeue
                    context.waitingTask.canceled = true;
                    context.waitingTask = null;
                }
            }

            context.taskShouldRun = true;

            if (needQueue) {
                stw = context.waitingTask = new SingletonTaskWorker(this);                    
            }
        }

        if (needQueue) {
            if (delay <= 0) 
                ses.execute(stw);
            else
                ses.schedule(stw, delay, unit);
        }
    }
}
