package net.floodlightcontroller.debugevent;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.debugevent.IDebugEventService.EventColumn;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.IPv4;

import org.openflow.protocol.OFFlowMod;
import org.openflow.util.HexString;

public class Event {
    long timestamp;
    long threadId;
    String threadName;
    Object eventData;
    private Map<String, String> returnMap;
    public Event(long timestamp, long threadId, String threadName, Object eventData) {
        super();
        this.timestamp = timestamp;
        this.threadId = threadId;
        this.threadName = threadName;
        this.eventData = eventData;
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

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public Object geteventData() {
        return eventData;
    }

    public void seteventData(Object eventData) {
        this.eventData = eventData;
    }

    @Override
    public String toString() {
        return "Event [timestamp=" + timestamp + ", threadId=" + threadId
               + ", eventData=" + eventData.toString() + "]";
    }

    public Map<String, String> getFormattedEvent(Class<?> eventClass, String moduleEventName) {
        if (eventClass == null || !eventClass.equals(eventData.getClass())) {
            returnMap = new HashMap<String, String>();
            returnMap.put("Error", "null event data or event-class does not match event-data");
            return returnMap;
        }
        // return cached value if there is one
        if (returnMap != null)
            return returnMap;

        returnMap = new HashMap<String, String>();
        returnMap.put("Timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                                            .format(timestamp));
        returnMap.put("Thread Id", String.valueOf(threadId));
        returnMap.put("Thread Name", String.valueOf(threadName));
        customFormat(eventClass, eventData, returnMap);
        return returnMap;
    }

    private void customFormat(Class<?> clazz, Object eventData,
                              Map<String, String> retMap) {
        for (Field f : clazz.getDeclaredFields()) {
            EventColumn ec = f.getAnnotation(EventColumn.class);
            if (ec == null) continue;
            f.setAccessible(true);
            try {
                Object obj =  f.get(eventData);

                switch(ec.description()) {
                    case DPID:
                        retMap.put(ec.name(), HexString.toHexString((Long) obj));
                        break;
                    case MAC:
                        retMap.put(ec.name(), HexString.toHexString((Long) obj, 6));
                        break;
                    case IPv4:
                        retMap.put(ec.name(), IPv4.fromIPv4Address((Integer) obj));
                        break;
                    case FLOW_MOD_FLAGS:
                        int flags = (Integer)obj;
                        StringBuilder builder = new StringBuilder();
                        if (flags == 0) {
                            builder.append("None");
                        }
                        else {
                            if ((flags & OFFlowMod.OFPFF_SEND_FLOW_REM) != 0) {
                                builder.append("SEND_FLOW_REM ");
                            }
                            if ((flags & OFFlowMod.OFPFF_CHECK_OVERLAP) != 0) {
                                builder.append("CHECK_OVERLAP ");
                            }
                            if ((flags & OFFlowMod.OFPFF_EMERG) != 0) {
                                builder.append("EMERG ");
                            }
                        }
                        retMap.put(ec.name(), builder.toString());
                        break;
                    case LIST_IPV4:
                        @SuppressWarnings("unchecked")
                        List<Integer> ipv4Addresses = (List<Integer>)obj;
                        StringBuilder ipv4AddressesStr = new StringBuilder();
                        if (ipv4Addresses.size() == 0) {
                            ipv4AddressesStr.append("--");
                        } else {
                            for (Integer ipv4Addr : ipv4Addresses) {
                                ipv4AddressesStr.append(IPv4.fromIPv4Address(ipv4Addr.intValue()));
                                ipv4AddressesStr.append(" ");
                            }
                        }
                        retMap.put(ec.name(), ipv4AddressesStr.toString());
                        break;
                    case LIST_ATTACHMENT_POINT:
                        @SuppressWarnings("unchecked")
                        List<SwitchPort> aps = (List<SwitchPort>)obj;
                        StringBuilder apsStr = new StringBuilder();
                        if (aps.size() == 0) {
                            apsStr.append("--");
                        } else {
                            for (SwitchPort ap : aps) {
                                apsStr.append(HexString.toHexString(ap.getSwitchDPID()));
                                apsStr.append("/");
                                apsStr.append(ap.getPort());
                                apsStr.append(" ");
                            }
                        }
                        retMap.put(ec.name(), apsStr.toString());
                        break;
                    case LIST_OBJECT:
                        @SuppressWarnings("unchecked")
                        List<Object> obl = (List<Object>)obj;
                        StringBuilder sbldr = new StringBuilder();
                        if (obl.size() == 0) {
                            sbldr.append("--");
                        } else {
                            for (Object o : obl) {
                                sbldr.append(o.toString());
                                sbldr.append(" ");
                            }
                        }
                        retMap.put(ec.name(), sbldr.toString());
                        break;
                    case SREF_LIST_OBJECT:
                        @SuppressWarnings("unchecked")
                        SoftReference<List<Object>> srefListObj =
                            (SoftReference<List<Object>>)obj;
                        List<Object> ol = srefListObj.get();
                        if (ol != null) {
                            StringBuilder sb = new StringBuilder();
                            if (ol.size() == 0) {
                                sb.append("--");
                            } else {
                                for (Object o : ol) {
                                    sb.append(o.toString());
                                    sb.append(" ");
                                }
                            }
                            retMap.put(ec.name(), sb.toString());
                        } else {
                            retMap.put(ec.name(), "-- reference not available --");
                        }
                        break;
                    case SREF_OBJECT:
                        @SuppressWarnings("unchecked")
                        SoftReference<Object> srefObj = (SoftReference<Object>)obj;
                        if (srefObj == null) {
                            retMap.put(ec.name(), "--");
                        } else {
                            Object o = srefObj.get();
                            if (o != null) {
                                retMap.put(ec.name(), o.toString());
                            } else {
                                retMap.put(ec.name(),
                                           "-- reference not available --");
                            }
                        }
                        break;
                    case STRING:
                    case OBJECT:
                    case PRIMITIVE:
                    default:
                        retMap.put(ec.name(), obj.toString());
                }
            } catch (ClassCastException e) {
                retMap.put("Error", e.getMessage());
            } catch (IllegalArgumentException e) {
                retMap.put("Error", e.getMessage());
            } catch (IllegalAccessException e) {
                retMap.put("Error", e.getMessage());
            }
        }
    }

}
