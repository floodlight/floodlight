package net.floodlightcontroller.debugevent;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.IllegalFormatConversionException;
import java.util.UnknownFormatConversionException;

import net.floodlightcontroller.packet.IPv4;

import org.openflow.util.HexString;

public class Event {
    long timestamp;
    long threadId;
    Object[] params;

    public Event(long timestamp, long threadId, Object[] params) {
        super();
        this.timestamp = timestamp;
        this.threadId = threadId;
        this.params = params;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }

    @Override
    public String toString() {
        return "Event [timestamp=" + timestamp + ", threadId=" + threadId
               + ", params=" + Arrays.toString(params) + "]";
    }

    public String toString(String formatStr, String moduleEventName) {
        String val = new StringBuilder().append(moduleEventName)
                        .append(" [")
                        .append(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(timestamp))
                        .append(", threadId=").append(threadId).append(", ")
                        .append(customFormat(formatStr, params))
                        .append("]").toString();
        return val;
    }

    private String customFormat(String formatStr, Object[] params) {
        StringBuilder result = new StringBuilder();
        String[] elem = formatStr.split(",");
        if (elem.length != params.length) {
            result.append(" format string does not match number of params - missing \',\'? ");
            return result.toString();
        }
        for (int index=0; index < elem.length; index++) {
            String[] temp = elem[index].split("%");
            if (temp.length != 2) {
                result.append(" incorrect format string - missing % ");
                continue;
            }
            if (temp[1].equals("dpid")) {
                try {
                    result.append(temp[0]).append(HexString.toHexString((Long)params[index]));
                } catch (ClassCastException e) {
                    result.append(e);
                }
            } else if (temp[1].equals("mac")) {
                try {
                    result.append(temp[0]).append(HexString.toHexString((Long)params[index], 6));
                } catch (ClassCastException e) {
                    result.append(e.toString());
                }
            } else if (temp[1].equals("ipv4")) {
                try {
                    result.append(temp[0]).append(IPv4.fromIPv4Address((Integer)params[index]));
                } catch (ClassCastException e) {
                    result.append(e.toString());
                }
            } else {
                try {
                    result.append(String.format(elem[index], params[index]));
                } catch (UnknownFormatConversionException e) {
                    result.append(temp[0]).append(e.toString());
                } catch (IllegalFormatConversionException e) {
                    result.append(temp[0]).append(e.toString());
                }
            }
            if (index+1 < elem.length) result.append(",");
        }

        return result.toString();
    }

}
