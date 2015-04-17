package net.floodlightcontroller.core.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.easymock.EasyMock;
import net.floodlightcontroller.core.OFSwitch;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFVersion;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * A sublcass of OFSwitchImpl that contains extra setters.
 * This class can be used for testing.
 * @author alexreimers
 *
 */
public class MockOFSwitchImpl extends OFSwitch {
    protected Map<OFStatsType, List<OFStatsReply>> statsMap;

    public MockOFSwitchImpl(MockOFConnection connection) {
        super(connection, OFFactories.getFactory(OFVersion.OF_10),
              EasyMock.createMock(IOFSwitchManager.class), connection.getDatapathId());
        statsMap = new HashMap<OFStatsType, List<OFStatsReply>>();
    }

    public void setBuffers(int buffers) {
        this.buffers = buffers;
    }

    public void setCapabilities(Set<OFCapabilities> cap) {
        this.capabilities = cap;
    }

    public void setAttributes(Map<Object, Object> attrs) {
        this.attributes.putAll(attrs);
    }


    @SuppressWarnings("unchecked")
    @Override
    public <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(OFStatsRequest<REPLY> request) {
        ListenableFuture<List<REPLY>> ofStatsFuture =
                EasyMock.createNiceMock(ListenableFuture.class);

        // We create a mock future and return info from the map
        try {
            OFStatsType statsType = request.getStatsType();
            List<REPLY> replies = (List<REPLY>) statsMap.get(statsType);
            EasyMock.expect(ofStatsFuture.get(EasyMock.anyLong(),
                    EasyMock.anyObject(TimeUnit.class))).andReturn(replies).anyTimes();
            EasyMock.expect(ofStatsFuture.get()).andReturn(replies).anyTimes();
            EasyMock.replay(ofStatsFuture);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ofStatsFuture;
    }

    public void addStatsRequest(OFStatsType type, List<OFStatsReply> reply) {
        statsMap.put(type, reply);
    }
}
