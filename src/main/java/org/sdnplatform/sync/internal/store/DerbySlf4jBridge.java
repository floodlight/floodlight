package org.sdnplatform.sync.internal.store;

import java.io.Writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Funnels Derby log outputs into an SLF4J logger. 
 */
public final class DerbySlf4jBridge
{

    private static final Logger logger = 
            LoggerFactory.getLogger(DerbySlf4jBridge.class);

    private DerbySlf4jBridge()
    {
    }
    
    /**
     * A basic adapter that funnels Derby's logs through an SLF4J logger.
     */
    public static final class LoggingWriter extends Writer
    {
        @Override
        public void write(final char[] cbuf, final int off, final int len)
        {
            if (!logger.isDebugEnabled()) return;
            
            // Don't bother with empty lines.
            if (len > 1)
            {
                logger.debug(new String(cbuf, off, len));
            }
        }

        @Override
        public void flush()
        {
            // noop.
        }

        @Override
        public void close()
        {
            // noop.
        }
    }

    public static String getBridgeMethod() {
        return DerbySlf4jBridge.class.getCanonicalName() + 
                ".bridge";
    }
    
    public static Writer bridge()
    {
        return new LoggingWriter();
    }
}