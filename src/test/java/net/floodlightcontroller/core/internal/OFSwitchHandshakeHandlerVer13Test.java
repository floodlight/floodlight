package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.easymock.Capture;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Test;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.OFConnection;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.OFSwitchAppHandshakePlugin.PluginResultType;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler.WaitAppHandshakeState;
import net.floodlightcontroller.core.util.URIUtil;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnection;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnectionState;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnectionsReply;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnectionsRequest;
import org.projectfloodlight.openflow.protocol.OFBsnGentableDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFBsnGentableDescStatsRequest;
import org.projectfloodlight.openflow.protocol.OFBsnSetAuxCxnsReply;
import org.projectfloodlight.openflow.protocol.OFBsnSetAuxCxnsRequest;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsRequest;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.protocol.OFRoleRequest;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.GenTableId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.OFPort;

import com.google.common.collect.ImmutableList;


public class OFSwitchHandshakeHandlerVer13Test extends OFSwitchHandlerTestBase {

    @Override
    public OFFactory getFactory() {
        return OFFactories.getFactory(OFVersion.OF_13);
    }

    @Override
    OFFeaturesReply getFeaturesReply() {
        return factory.buildFeaturesReply()
                .setDatapathId(dpid)
                .setNBuffers(1)
                .setNTables((short)1)
                .setCapabilities(EnumSet.<OFCapabilities>of(OFCapabilities.FLOW_STATS, OFCapabilities.TABLE_STATS))
                .setAuxiliaryId(OFAuxId.MAIN)
                .build();
    }

    OFPortDescStatsReply getPortDescStatsReply() {
        OFPortDesc portDesc = factory.buildPortDesc()
                .setName("Eth1")
                .setPortNo(OFPort.of(1))
                .build();
        return factory.buildPortDescStatsReply()
            .setEntries(ImmutableList.<OFPortDesc>of(portDesc))
            .build();
    }


    /** Move the channel from scratch to WAIT_CONFIG_REPLY state
     * Builds on moveToWaitFeaturesReply
     * adds testing for WAIT_FEATURES_REPLY state
     */
    @Test
    public void moveToWaitPortDescStatsReply() throws Exception {
        testInitState();

        switchHandler.beginHandshake();

        OFMessage msg = connection.retrieveMessage();
        assertThat(msg, CoreMatchers.instanceOf(OFPortDescStatsRequest.class));
        verifyUniqueXids(msg);

        assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitPortDescStatsReplyState.class));
    }

    @Override
    void moveToPreConfigReply() throws Exception {
        moveToWaitPortDescStatsReply();
        switchHandler.processOFMessage(getPortDescStatsReply());
    }

    public void handleDescStatsAndCreateSwitch() throws Exception {
        // build the stats reply
        OFDescStatsReply sr = createDescriptionStatsReply();

        reset(sw);
        SwitchDescription switchDescription = new SwitchDescription(sr);
        setupSwitchForInstantiationWithReset();
        sw.setPortDescStats(anyObject(OFPortDescStatsReply.class));
        expectLastCall().once();
        replay(sw);

        reset(switchManager);
        expect(switchManager.getHandshakePlugins()).andReturn(plugins).anyTimes();
        expect(
               switchManager.getOFSwitchInstance(anyObject(OFConnection.class),
                                              eq(switchDescription),
                                              anyObject(OFFactory.class),
                                              anyObject(DatapathId.class))).andReturn(sw).once();
        expect(switchManager.getNumRequiredConnections()).andReturn(0);
        switchManager.switchAdded(sw);
        expectLastCall().once();
        replay(switchManager);

        // send the description stats reply
        switchHandler.processOFMessage(sr);

        verify(sw, switchManager);
    }

    /** This makes sure the correct behavior occurs for an illegal OF Aux Reply status
     */
    @Test
    public void testOFAuxSwitchFail() throws Exception {
        //moveToWaitOFAuxCxnsReply();

        // Build and OF Aux reply - status of non zero denotes failure on switch end
        OFBsnSetAuxCxnsReply auxReply = factory.buildBsnSetAuxCxnsReply()
                .setNumAux(0)
                .setStatus(-1)
                .build();

        verifyExceptionCaptured(auxReply, OFAuxException.class);
    }

    @Test
    @Override
    public void moveToWaitAppHandshakeState() throws Exception {
        //moveToWaitGenDescStatsReply();

        //handleGenDescStatsReplay(true);

        assertThat(switchHandler.getStateForTesting(),
                   CoreMatchers.instanceOf(WaitAppHandshakeState.class));
    }

    @Override
    Class<?> getRoleRequestClass() {
        return OFRoleRequest.class;
    }

    @Override
    public void verifyRoleRequest(OFMessage m, OFControllerRole expectedRole) {
        assertThat(m, CoreMatchers.instanceOf(OFRoleRequest.class));
        OFRoleRequest roleRequest = (OFRoleRequest) m;
        assertThat(roleRequest.getRole(), equalTo(expectedRole));
    }

    @Override
    protected OFMessage getRoleReply(long xid, OFControllerRole role) {
        OFRoleReply roleReply = factory.buildRoleReply()
                .setXid(xid)
                .setRole(role)
                .build();
        return roleReply;
    }

    @Test
    public void moveToWaitControllerCxnsReplyState() throws Exception {
        moveToWaitAppHandshakeState();

        assertThat(switchHandler.getStateForTesting(),
                   CoreMatchers.instanceOf(WaitAppHandshakeState.class));


        WaitAppHandshakeState state = (WaitAppHandshakeState) switchHandler.getStateForTesting();
        PluginResult result = new PluginResult(PluginResultType.CONTINUE);
        state.exitPlugin(result);

        OFMessage msg = connection.retrieveMessage();
        assertThat(msg, CoreMatchers.instanceOf(OFBsnControllerConnectionsRequest.class));
        verifyUniqueXids(msg);

        //assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitControllerCxnsReplyState.class));
    }

    @Override
    @Test
    public void moveToWaitInitialRole()
            throws Exception {
        moveToWaitControllerCxnsReplyState();

        //assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(WaitControllerCxnsReplyState.class));

        OFBsnControllerConnection cxn = factory.buildBsnControllerConnection()
                .setAuxiliaryId(OFAuxId.MAIN)
                .setRole(OFControllerRole.ROLE_MASTER)
                .setState(OFBsnControllerConnectionState.BSN_CONTROLLER_CONNECTION_STATE_CONNECTED)
                .setUri(URIUtil.createURI("1.2.3.4", 6653).toString())
                .build();

        List<OFBsnControllerConnection> cxns = new ArrayList<OFBsnControllerConnection>();
        cxns.add(cxn);

        // build the controller connections reply
        OFBsnControllerConnectionsReply cxnsReply = factory.buildBsnControllerConnectionsReply()
                .setConnections(cxns)
                .build();

        reset(sw);
        sw.updateControllerConnections(cxnsReply);
        expectLastCall().once();
        expect(sw.getAttribute(IOFSwitchBackend.SWITCH_SUPPORTS_NX_ROLE)).andReturn(true).anyTimes();
        replay(sw);

        reset(roleManager);
        expect(roleManager.getOFControllerRole()).andReturn(OFControllerRole.ROLE_MASTER).anyTimes();
        roleManager.notifyControllerConnectionUpdate();
        expectLastCall().once();
        replay(roleManager);

        // send the controller connections reply
        switchHandler.processOFMessage(cxnsReply);

        // Expect wait initial role's enterState message to be written
        OFMessage msg = connection.retrieveMessage();
        assertThat(msg, CoreMatchers.instanceOf(OFRoleRequest.class));
        verifyUniqueXids(msg);

        assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));
    }

    @Override
    @Test
    public void moveToWaitSwitchDriverSubHandshake() throws Exception {
        //moveToWaitGenDescStatsReply();
        //handleGenDescStatsReplay(false);

        assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitSwitchDriverSubHandshakeState.class));
        assertThat("Unexpected message captured", connection.getMessages(), Matchers.empty());
        verify(sw);
    }

}
