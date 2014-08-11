package net.floodlightcontroller.debugevent;

import java.lang.ref.SoftReference;
import java.util.Collection;

import javax.annotation.Nullable;

import net.floodlightcontroller.debugevent.EventResource.EventResourceBuilder;
import net.floodlightcontroller.debugevent.EventResource.Metadata;
import net.floodlightcontroller.devicemanager.SwitchPort;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.util.HexString;

import net.floodlightcontroller.packet.IPv4;

import com.google.common.base.Joiner;

class CustomFormatterCollectionAttachmentPoint implements
    CustomFormatter<Collection<SwitchPort>> {

    @Override
    public EventResourceBuilder
            customFormat(@Nullable Collection<SwitchPort> aps2, String name,
                         EventResourceBuilder edb) {
        if (aps2 != null) {
            StringBuilder apsStr2 = new StringBuilder();
            if (aps2.size() == 0) {
                apsStr2.append("--");
            } else {
                for (SwitchPort ap : aps2) {
                    apsStr2.append(ap.getSwitchDPID().toString());
                    apsStr2.append("/");
                    apsStr2.append(ap.getPort());
                    apsStr2.append(" ");
                }
                // remove trailing space
                apsStr2.deleteCharAt(apsStr2.length());
            }
            edb.dataFields.add(new Metadata(name, apsStr2.toString()));
        }
        return edb;
    }
}

class CustomFormatterCollectionIpv4 implements
    CustomFormatter<Collection<IPv4Address>> {

    @Override
    public EventResourceBuilder
            customFormat(@Nullable Collection<IPv4Address> ipv4Addresses2,
                         String name, EventResourceBuilder edb) {
        if (ipv4Addresses2 != null) {
            String ipv4AddressesStr2 = "--";
            if (!ipv4Addresses2.isEmpty()) {
                ipv4AddressesStr2 = Joiner.on(" ").join(ipv4Addresses2);
            }
            edb.dataFields.add(new Metadata(name, ipv4AddressesStr2));
        }
        return edb;
    }
}

class CustomFormatterCollectionObject implements
    CustomFormatter<Collection<Object>> {

    @Override
    public EventResourceBuilder
            customFormat(@Nullable Collection<Object> obl2, String name,
                         EventResourceBuilder edb) {
        if (obl2 != null) {
            StringBuilder sbldr2 = new StringBuilder();
            if (obl2.size() == 0) {
                sbldr2.append("--");
            } else {
                for (Object o : obl2) {
                    sbldr2.append(o.toString());
                    sbldr2.append(" ");
                }
            }
            edb.dataFields.add(new Metadata(name, sbldr2.toString()));
        }
        return edb;
    }
}

class CustomFormatterDpid implements CustomFormatter<DatapathId> {

    @Override
    public EventResourceBuilder customFormat(@Nullable DatapathId dpid,
                                             String name,
                                             EventResourceBuilder edb) {
        if (dpid != null) {
            edb.dataFields.add(new Metadata(name, dpid.toString()));
        }
        return edb;
    }

}

class CustomFormatterIpv4 implements CustomFormatter<Integer> {

    @Override
    public EventResourceBuilder customFormat(@Nullable Integer obj,
                                             String name,
                                             EventResourceBuilder edb) {
        if (obj != null) {
            edb.dataFields.add(new Metadata(name, IPv4.fromIPv4Address(obj)));
        }
        return edb;
    }

}

class CustomFormatterMac implements CustomFormatter<Long> {

    @Override
    public EventResourceBuilder customFormat(@Nullable Long obj,
                                             String name,
                                             EventResourceBuilder edb) {
        if (obj != null) {
            edb.dataFields.add(new Metadata(name, HexString.toHexString(obj,
                                                                        6)));
        }
        return edb;
    }

}

class CustomFormatterObject implements CustomFormatter<Object> {

    @Override
    public EventResourceBuilder customFormat(@Nullable Object obj,
                                             String name,
                                             EventResourceBuilder edb) {
        if (obj != null) {
            edb.dataFields.add(new Metadata(name, obj.toString()));
        }
        return edb;
    }

}

class CustomFormatterPrimitive implements CustomFormatter<Object> {

    @Override
    public EventResourceBuilder customFormat(@Nullable Object obj,
                                             String name,
                                             EventResourceBuilder edb) {
        if (obj != null) {
            edb.dataFields.add(new Metadata(name, obj.toString()));
        }
        return edb;
    }

}

class CustomFormatterSrefCollectionObject implements
    CustomFormatter<SoftReference<Collection<Object>>> {

    @Override
    public EventResourceBuilder customFormat(@Nullable
                                         SoftReference<Collection<Object>> srefCollectionObj2,
                                         String name, EventResourceBuilder edb) {
        if (srefCollectionObj2 != null) {
            Collection<Object> ol2 = srefCollectionObj2.get();
            if (ol2 != null) {
                StringBuilder sb = new StringBuilder();
                if (ol2.size() == 0) {
                    sb.append("--");
                } else {
                    for (Object o : ol2) {
                        sb.append(o.toString());
                        sb.append(" ");
                    }
                }
                edb.dataFields.add(new Metadata(name, sb.toString()));
            } else {
                edb.dataFields.add(new Metadata(name,
                                                "-- reference not available --"));
            }
        }
        return edb;
    }

}

class CustomFormatterSrefObject implements CustomFormatter<SoftReference<Object>> {

    @Override
    public EventResourceBuilder
            customFormat(@Nullable SoftReference<Object> srefObj,
                         String name, EventResourceBuilder edb) {
        if (srefObj != null) {
            Object o = srefObj.get();
            if (o != null) {
                edb.dataFields.add(new Metadata(name, o.toString()));
            } else {
                edb.dataFields.add(new Metadata(name,
                                                "-- reference not available --"));
            }
        }
        return edb;
    }

}

class CustomFormatterString implements CustomFormatter<String> {

    @Override
    public EventResourceBuilder customFormat(@Nullable String string,
                                             String name,
                                             EventResourceBuilder edb) {
        if (string != null) {
            edb.dataFields.add(new Metadata(name, string));
        }
        return edb;
    }

}
