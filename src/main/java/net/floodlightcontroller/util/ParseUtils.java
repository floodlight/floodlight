package net.floodlightcontroller.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple class to assist in parsing hex or decimal primitive types
 * from String to their respective types.
 * 
 * Use this in place of e.g. Integer.decode() if you wish to avoid 
 * unnecessarily creating objects when you want just the primitive type.
 *  
 * @author rizard
 *
 */
public class ParseUtils {
    private static final Logger log = LoggerFactory.getLogger(ParseUtils.class);
    private static final byte exceptionReturnValue = 0;
    
    /**
     * Parse an int from a String.
     * 
     * Hex is expected to have a leading "0x".
     * 
     * @throws NumberFormatException
     * @param s
     * @return
     */
    public static int parseHexOrDecInt(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        s = s.trim().toLowerCase();
        if (s.startsWith("0x")) {
            return Integer.parseInt(s.replaceFirst("0x", ""), 16);
        } else {
            return Integer.parseInt(s);                   
        }
    }

    /**
     * Parse a short from a String.
     * 
     * Hex is expected to have a leading "0x".
     * 
     * @throws NumberFormatException
     * @param s
     * @return
     */
    public static short parseHexOrDecShort(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        s = s.trim().toLowerCase();
        try {
            if (s.startsWith("0x")) {
                return Short.parseShort(s.replaceFirst("0x", ""), 16);
            } else {
                return Short.parseShort(s);                   
            }
        } catch (NumberFormatException e) {
            log.error("Could not parse short {}. Returning default", s);
            return exceptionReturnValue;
        }
    }

    /**
     * Parse a long from a String.
     * 
     * Hex is expected to have a leading "0x".
     * 
     * @throws NumberFormatException
     * @param s
     * @return
     */
    public static long parseHexOrDecLong(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        s = s.trim().toLowerCase();
        if (s.startsWith("0x")) {
            return Long.parseLong(s.replaceFirst("0x", ""), 16);
        } else {
            return Long.parseLong(s);                   
        }
    }

    /**
     * Parse a byte from a String.
     * 
     * Hex is expected to have a leading "0x".
     * 
     * @throws NumberFormatException
     * @param s
     * @return
     */
    public static byte parseHexOrDecByte(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        s = s.trim().toLowerCase();
        if (s.startsWith("0x")) {
            return Byte.parseByte(s.replaceFirst("0x", ""), 16);
        } else {
            return Byte.parseByte(s);                   
        }
    }

    /**
     * Convert any number to a boolean, where any positive number is
     * true and zero or negative number is false. The largest supported
     * type is a Long.
     * 
     * Hex is expected to have a leading "0x".
     * 
     * @throws NumberFormatException
     * @param s
     * @return true or false
     */
    public static boolean parseHexOrDecBool(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        s = s.trim().toLowerCase();
        if (s.startsWith("0x")) {
            return Long.parseLong(s.replaceFirst("0x", ""), 16) > 0;
        } else {
            return Long.parseLong(s) > 0;                   
        }
    }
}
