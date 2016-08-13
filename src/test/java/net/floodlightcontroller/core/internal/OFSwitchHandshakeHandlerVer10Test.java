package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;

import java.util.EnumSet;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Test;

import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.OFSwitchAppHandshakePlugin.PluginResultType;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler.WaitAppHandshakeState;

import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRoleReply;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRoleRequest;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import com.google.common.collect.ImmutableList;


public class OFSwitchHandshakeHandlerVer10Test extends OFSwitchHandlerTestBase {

    @Override
    public OFFactory getFactory() {
        return OFFactories.getFactory(OFVersion.OF_10);
    }

    @Override
    OFFeaturesReply getFeaturesReply() {
        OFPortDesc portDesc = factory.buildPortDesc()
                .setName("Eth1")
                .setPortNo(OFPort.of(1))
                .build();
        return factory.buildFeaturesReply()
                .setDatapathId(dpid)
                .setNBuffers(1)
                .setNTables((short)1)
                .setCapabilities(EnumSet.<OFCapabilities>of(OFCapabilities.FLOW_STATS, OFCapabilities.TABLE_STATS))
                .setActions(EnumSet.<OFActionType>of(OFActionType.SET_VLAN_PCP))
                .setPorts(ImmutableList.<OFPortDesc>of(portDesc))
                .build();
    }

    @Override
    void moveToPreConfigReply() throws Exception {
        testInitState();
        switchHandler.beginHandshake();
    }

    public void handleDescStatsAndCreateSwitch(boolean switchDriverComplete) throws Exception {
        // build the stats reply
        OFDescStatsReply sr = createDescriptionStatsReply();

        reset(sw);
        SwitchDescription switchDescription = new SwitchDescription(sr);
        setupSwitchForInstantiationWithReset();
        sw.startDriverHandshake();
        expectLastCall().once();
        expect(sw.getOFFactory()).andReturn(factory).once();
        sw.isDriverHandshakeComplete();
        expectLastCall().andReturn(switchDriverComplete).once();

        if(factory.getVersion().compareTo(OFVersion.OF_13) >= 0) {
            sw.setPortDescStats(anyObject(OFPortDescStatsReply.class));
            expectLastCall().once();
        }

        replay(sw);

        reset(switchManager);
        expect(switchManager.getHandshakePlugins()).andReturn(plugins).anyTimes();
        expect(
               switchManager.getOFSwitchInstance(anyObject(OFConnection.class),
                                              eq(switchDescription),
                                              anyObject(OFFactory.class),
                                              anyObject(DatapathId.class))).andReturn(sw).once();
        switchManager.switchAdded(sw);
        expectLastCall().once();
        replay(switchManager);

        // send the description stats reply
        switchHandler.processOFMessage(sr);
    }

    @Test
    @Override
    public void moveToWaitAppHandshakeState() throws Exception {
        moveToWaitDescriptionStatReply();
        handleDescStatsAndCreateSwitch(true);
        assertThat(switchHandler.getStateForTesting(),
                CoreMatchers.instanceOf(WaitAppHandshakeState.class));
    }

    @Override
    Class<?> getRoleRequestClass() {
        return OFNiciraControllerRoleRequest.class;
    }

    @Override
    public void verifyRoleRequest(OFMessage m, OFControllerRole expectedRole) {
        assertThat(m.getType(), equalTo(OFType.EXPERIMENTER));
        OFNiciraControllerRoleRequest roleRequest = (OFNiciraControllerRoleRequest)m;
        assertThat(roleRequest.getRole(), equalTo(NiciraRoleUtils.ofRoleToNiciraRole(expectedRole)));
    }

    @Override
    protected OFMessage getRoleReply(long xid, OFControllerRole role) {
        OFNiciraControllerRoleReply roleReply = factory.buildNiciraControllerRoleReply()
                .setXid(xid)
                .setRole(NiciraRoleUtils.ofRoleToNiciraRole(role))
                .build();
        return roleReply;
    }

    @Override
    @Test
    public void moveToWaitSwitchDriverSubHandshake() throws Exception {
        moveToWaitDescriptionStatReply();
        handleDescStatsAndCreateSwitch(false);

        assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitSwitchDriverSubHandshakeState.class));
        assertThat("Unexpected message captured", connection.getMessages(), Matchers.empty());
        verify(sw);
    }

    @Override
    @Test
    public void moveToWaitInitialRole()
            throws Exception {
        moveToWaitAppHandshakeState();

        assertThat(switchHandler.getStateForTesting(),
                   CoreMatchers.instanceOf(WaitAppHandshakeState.class));

        reset(sw);
        expect(sw.getAttribute(IOFSwitchBackend.SWITCH_SUPPORTS_NX_ROLE)).andReturn(true).anyTimes();
        replay(sw);

        reset(roleManager);
        expect(roleManager.getOFControllerRole()).andReturn(OFControllerRole.ROLE_MASTER).anyTimes();
        replay(roleManager);

        WaitAppHandshakeState state = (WaitAppHandshakeState) switchHandler.getStateForTesting();
        PluginResult result = new PluginResult(PluginResultType.CONTINUE);
        state.exitPlugin(result);

        assertThat(connection.retrieveMessage(), instanceOf(getRoleRequestClass()));
        assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));
    }
}
