package net.floodlightcontroller.core.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicate the run() method of a floodlight module
 * @author readams
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Run {
    /** declares this run method as the application main method. Will be called last and is not expected to
     *  return. It is a configuration error to have more than one module declaring a main method.
     * @return
     */
    boolean mainLoop() default false;
}
