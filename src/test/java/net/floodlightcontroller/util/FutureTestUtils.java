package net.floodlightcontroller.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import junit.framework.AssertionFailedError;

public class FutureTestUtils {

    private FutureTestUtils() { }

    @SuppressWarnings("unchecked")
    public static <T extends Exception> T assertFutureFailedWithException(Future<?> future,
            Class<T> clazz) throws InterruptedException {

        assertThat("Future should be complete ", future.isDone(), equalTo(true));
        try {
            future.get();
            throw new AssertionFailedError("Expected ExecutionExcepion");
        } catch(ExecutionException e) {
            assertThat(e.getCause(), instanceOf(clazz));
            return (T) e.getCause();
        }
    }

}
