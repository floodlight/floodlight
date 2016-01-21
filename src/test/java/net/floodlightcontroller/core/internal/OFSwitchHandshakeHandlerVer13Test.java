package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.EnumSet;
import java.util.List;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Test;

import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler.WaitAppHandshakeState;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler.WaitTableFeaturesReplyState;

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
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsRequest;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
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
       
        expect(sw.getOFFactory()).andReturn(factory).once();
        replay(sw);

        reset(switchManager);
        expect(switchManager.getHandshakePlugins()).andReturn(plugins).anyTimes();
        expect(
               switchManager.getOFSwitchInstance(anyObject(OFConnection.class),
                                              eq(switchDescription),
                                              anyObject(OFFactory.class),
                                              anyObject(DatapathId.class))).andReturn(sw).once();
        expect(switchManager.getNumRequiredConnections()).andReturn(1).anyTimes(); 
        switchManager.switchAdded(sw);
        expectLastCall().once();
        replay(switchManager);

        // send the description stats reply
        switchHandler.processOFMessage(sr);

        OFMessage msg = connection.retrieveMessage();
        assertThat(msg, CoreMatchers.instanceOf(OFTableFeaturesStatsRequest.class));
        verifyUniqueXids(msg);
        
        verify(sw, switchManager);
    }
    
    @SuppressWarnings("unchecked")
	public void handleTableFeatures(boolean subHandShakeComplete) throws Exception {
    	// build the table features stats reply
    	OFTableFeaturesStatsReply tf = createTableFeaturesStatsReply();
    	
    	reset(sw);
    	sw.startDriverHandshake();
        expectLastCall().once();
        expect(sw.isDriverHandshakeComplete()).andReturn(subHandShakeComplete).once();
    	sw.processOFTableFeatures(anyObject(List.class));
    	expectLastCall().once();
    	expect(sw.getOFFactory()).andReturn(factory).anyTimes();
    	replay(sw);
    	
    	switchHandler.processOFMessage(tf);
    }
    
    @Test
    public void moveToWaitTableFeaturesReplyState() throws Exception {
    	moveToWaitDescriptionStatReply();
    	handleDescStatsAndCreateSwitch();
    	
        assertThat(switchHandler.getStateForTesting(),
                   CoreMatchers.instanceOf(WaitTableFeaturesReplyState.class));
    }

    @Test
    @Override
    public void moveToWaitAppHandshakeState() throws Exception {
    	moveToWaitTableFeaturesReplyState();
    	handleTableFeatures(true);
    	
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

    @Override
    @Test
    public void moveToWaitInitialRole() throws Exception {
    	moveToWaitAppHandshakeState();
        assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(WaitAppHandshakeState.class));

        reset(sw);
        expect(sw.getAttribute(IOFSwitchBackend.SWITCH_SUPPORTS_NX_ROLE)).andReturn(true).anyTimes();
        replay(sw);
        
        reset(roleManager);
        expect(roleManager.getOFControllerRole()).andReturn(OFControllerRole.ROLE_MASTER).anyTimes();
        roleManager.notifyControllerConnectionUpdate();
        expectLastCall().once();
        replay(roleManager);
        
        WaitAppHandshakeState state = (WaitAppHandshakeState) switchHandler.getStateForTesting();
        state.enterNextPlugin();

        // Expect wait initial role's enterState message to be written
        OFMessage msg = connection.retrieveMessage();
        assertThat(msg, CoreMatchers.instanceOf(OFRoleRequest.class));
        verifyUniqueXids(msg);

        assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));
    }

    @Override
    @Test
    public void moveToWaitSwitchDriverSubHandshake() throws Exception {
        moveToWaitTableFeaturesReplyState();
        handleTableFeatures(false); //TODO

        assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitSwitchDriverSubHandshakeState.class));
        assertThat("Unexpected message captured", connection.getMessages(), Matchers.empty());
        verify(sw);
    }

}