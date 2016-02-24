package net.floodlightcontroller.debugevent;

import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.debugevent.IDebugEventService.EventFieldType;
import net.floodlightcontroller.debugevent.IDebugEventService.EventType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

public class DebugEventAppender<E> extends UnsynchronizedAppenderBase<E> {
    static IDebugEventService debugEvent;
    static IEventCategory<WarnErrorEvent> evWarnError;
    static final Thread debugEventRegistryTask;
    static {
        debugEventRegistryTask = new Thread() {
            @Override
            public void run() {
                while (DebugEventAppender.debugEvent == null) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                // safe to register debugEvent
                registerDebugEventQueue();
            }
        };
        debugEventRegistryTask.setDaemon(true);
    }

    @Override
    public void start() {
        DebugEventAppender.debugEventRegistryTask.start();
        super.start();
    }

    public static void
            setDebugEventServiceImpl(IDebugEventService debugEvent) {
        DebugEventAppender.debugEvent = debugEvent;
        // It is now ok to register an event Q - but letting this thread go
        // since it was called from a startUp() routine
    }

    /**
     * The logging system calls append for every log message. This method
     * filters out the WARN and ERROR message and adds to a debug event queue
     * that can be accessed via cli or rest-api or gui.
     */
    @Override
    protected void append(E eventObject) {
        if (!isStarted()) {
            return;
        }
        if (evWarnError != null) {
            ILoggingEvent ev = ((ILoggingEvent) eventObject);
            if (ev.getLevel().equals(Level.ERROR)
                || ev.getLevel().equals(Level.WARN)) {
                evWarnError
                .newEventWithFlush(new WarnErrorEvent(ev.getFormattedMessage(),
                                                      ev.getLevel(),
                                                      ev.getThreadName(),
                                                      ev.getLoggerName()));
            }
        }
    }

    private static void registerDebugEventQueue() {
        evWarnError = debugEvent.buildEvent(WarnErrorEvent.class)
                .setModuleName("net.floodlightcontroller.core")
                .setEventName("warn-error-queue")
                .setEventDescription("all WARN and ERROR logs")
                .setEventType(EventType.ALWAYS_LOG)
                .setBufferCapacity(100)
                .setAckable(false)
                .register();

    }

    public static class WarnErrorEvent {
        @EventColumn(name = "message", description = EventFieldType.STRING)
        String message;

        @EventColumn(name = "level", description = EventFieldType.OBJECT)
        Level level;

        @EventColumn(name = "threadName",
                     description = EventFieldType.STRING)
        String threadName;

        @EventColumn(name = "logger", description = EventFieldType.OBJECT)
        String logger;

        public WarnErrorEvent(String message, Level level,
                              String threadName, String logger) {
            this.message = message;
            this.level = level;
            this.threadName = threadName;
            this.logger = logger;
        }
    }

}
