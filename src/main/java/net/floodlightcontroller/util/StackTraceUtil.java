package net.floodlightcontroller.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/** 
 * Utility class to get a stacktrace as a nice string
 * 
 * @author gregor
 *
 */

public class StackTraceUtil {
    public static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
