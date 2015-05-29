package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.resetToStrict;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.easymock.EasyMock;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.SwitchStatus;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.PortChangeEvent;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.OFSwitchAppHandshakePlugin.PluginResultType;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler.QuarantineState;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler.WaitAppHandshakeState;
import net.floodlightcontroller.debugcounter.DebugCounterServiceImpl;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.projectfloodlight.openflow.protocol.OFBadActionCode;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortReason;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFSetConfig;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFTableFeatureProp;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.util.LinkedHashSetWrapper;
import net.floodlightcontroller.util.OrderedCollection;

import com.google.common.collect.ImmutableList;


public abstract class OFSwitchHandlerTestBase {
	protected static final DatapathId dpid = DatapathId.of(0x42L);

	protected IOFSwitchManager switchManager;
	protected RoleManager roleManager;

	private IDebugCounterService debugCounterService;
	protected OFSwitchHandshakeHandler switchHandler;
	protected MockOFConnection connection;
	// Use a 1.0 factory for the 1.0 test
	protected final OFFactory factory = getFactory();

	protected OFFeaturesReply featuresReply;
	protected List<IAppHandshakePluginFactory> plugins;

	private HashSet<Long> seenXids = null;
	protected IOFSwitchBackend sw;
	private Timer timer;
	private TestHandshakePlugin handshakePlugin;

	private class TestHandshakePlugin extends OFSwitchAppHandshakePlugin {
		protected TestHandshakePlugin(PluginResult defaultResult, int timeoutS) {
			super(defaultResult, timeoutS);
		}

		@Override
		protected void processOFMessage(OFMessage m) {
		}

		@Override
		protected void enterPlugin() {
		}
	}

	
	public void setUpFeaturesReply() {
		getFeaturesReply();
		this.featuresReply = getFeaturesReply();

		// Plugin set
		IAppHandshakePluginFactory factory = createMock(IAppHandshakePluginFactory.class);
		PluginResult result = new PluginResult(PluginResultType.QUARANTINE, "test quarantine");
		handshakePlugin = new TestHandshakePlugin(result, 5);
		expect(factory.createPlugin()).andReturn(handshakePlugin).anyTimes();
		replay(factory);
		plugins = ImmutableList.of(factory);
	}


	@Before
	public void setUp() throws Exception {
		/*
		 * This needs to be called explicitly to ensure the featuresReply is not null.
		 * Otherwise, there is no guarantee @Before will for setUpFeaturesReply() will
		 * call that function before our @Before setUp() here.
		 */
		setUpFeaturesReply(); 
		switchManager = createMock(IOFSwitchManager.class);
		roleManager = createMock(RoleManager.class);
		sw = createMock(IOFSwitchBackend.class);
		timer = createMock(Timer.class);
		expect(timer.newTimeout(anyObject(TimerTask.class), anyLong(), anyObject(TimeUnit.class))).andReturn(EasyMock.createNiceMock(Timeout.class));
		replay(timer);
		seenXids = null;

		// TODO: should mock IDebugCounterService and make sure
		// the expected counters are updated.
		debugCounterService = new DebugCounterServiceImpl();
		SwitchManagerCounters counters =
				new SwitchManagerCounters(debugCounterService);
		expect(switchManager.getCounters()).andReturn(counters).anyTimes();
		replay(switchManager);
		connection = new MockOFConnection(featuresReply.getDatapathId(), OFAuxId.MAIN);
		switchHandler = new OFSwitchHandshakeHandler(connection, featuresReply, switchManager, roleManager, timer);

		// replay sw. Reset it if you need more specific behavior
		replay(sw);
	}


	@After
	public void tearDown() {
		verifyAll();
	}

	private void verifyAll() {
		assertThat("Unexpected messages have been captured",
				connection.getMessages(),
				Matchers.empty());
		// verify all mocks.
		verify(sw);
	}

	void verifyUniqueXids(OFMessage... msgs) {
		verifyUniqueXids(Arrays.asList(msgs));
	}

	/** make sure that the transaction ids in the given messages are
	 * not 0 and differ between each other.
	 * While it's not a defect per se if the xids are we want to ensure
	 * we use different ones for each message we send.
	 */
	void verifyUniqueXids(List<OFMessage> msgs) {
		if (seenXids == null)
			seenXids = new HashSet<Long>();
		for (OFMessage m: msgs)  {
			long xid = m.getXid();
			assertTrue("Xid in messags is 0", xid != 0);
			assertFalse("Xid " + xid + " has already been used",
					seenXids.contains(xid));
			seenXids.add(xid);
		}
	}


	/*************************** abstract phases / utilities to be filled in by the subclasses */

	// Factory + messages

	/** @return the version-appropriate factory */
	public abstract OFFactory getFactory();

	/**
	 * @return a version appropriate features reply (different in 1.3 because it
	 * doesn't have ports)
	 */
	abstract OFFeaturesReply getFeaturesReply();
	/** @return the class that's used for role requests/replies (OFNiciraRoleRequest vs.
	 *  OFRequest)
	 */

	/// Role differences

	abstract Class<?> getRoleRequestClass();
	/** Verify that the given OFMessage is a correct RoleRequest message
	 * for the given role using the given xid (for the version).
	 */
	public abstract void verifyRoleRequest(OFMessage m,
			OFControllerRole expectedRole);
	/** Return a RoleReply message for the given role */
	protected abstract OFMessage getRoleReply(long xid, OFControllerRole role);

	/// Difference in the handshake sequence

	/** OF1.3 has the PortDescStatsRequest, OF1.0 not */
	abstract void moveToPreConfigReply() throws Exception;
	/**
	 * Move the channel from scratch to WaitAppHandshakeState
	 * Different for OF1.0 and OF1.3 because of GenTables.
	 * @throws Exception
	 */
	@Test
	public abstract void moveToWaitAppHandshakeState() throws Exception;

	/**
	 * Move the channel from scratch to WaitSwitchDriverSubHandshake
	 * Different for OF1.0 and OF1.3 because of GenTables.
	 * @throws Exception
	 */
	@Test
	public abstract void moveToWaitSwitchDriverSubHandshake() throws Exception;

	/**
	 * Move the channel from scratch to WaitInitialRole
	 * Different for OF1.0 and OF1.3 because of Controller Connections.
	 * @throws Exception
	 */
	@Test
	public abstract void moveToWaitInitialRole() throws Exception;

	/*******************************************************************************************/


	/** Move the channel from scratch to INIT state
        This occurs upon creation of the switch handler
	 */
	@Test
	public void testInitState() throws Exception {
		assertThat(connection.getListener(), notNullValue());
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.InitState.class));
	}


	/** Move the channel from scratch to WAIT_CONFIG_REPLY state
	 * adds testing for beginHandshake() which moves the state from
	 * InitState to WaitConfigReply.
	 */
	@Test
	public void moveToWaitConfigReply() throws Exception {
		moveToPreConfigReply();

		List<OFMessage> msgs = connection.getMessages();
		assertEquals(3, msgs.size());
		assertEquals(OFType.SET_CONFIG, msgs.get(0).getType());
		OFSetConfig sc = (OFSetConfig)msgs.get(0);
		assertEquals(0xffff, sc.getMissSendLen());
		assertEquals(OFType.BARRIER_REQUEST, msgs.get(1).getType());
		assertEquals(OFType.GET_CONFIG_REQUEST, msgs.get(2).getType());
		verifyUniqueXids(msgs);
		msgs.clear();
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitConfigReplyState.class));
		verifyAll();
	}



	/** Move the channel from scratch to WAIT_DESCRIPTION_STAT_REPLY state
	 * Builds on moveToWaitConfigReply()
	 * adds testing for WAIT_CONFIG_REPLY state
	 */
	@Test
	public void moveToWaitDescriptionStatReply() throws Exception {
		moveToWaitConfigReply();

		connection.clearMessages();
		OFGetConfigReply cr = factory.buildGetConfigReply()
				.setMissSendLen(0xFFFF)
				.build();

		switchHandler.processOFMessage(cr);

		OFMessage msg = connection.retrieveMessage();
		assertEquals(OFType.STATS_REQUEST, msg.getType());
		OFStatsRequest<?> sr = (OFStatsRequest<?>)msg;
		assertEquals(OFStatsType.DESC, sr.getStatsType());
		verifyUniqueXids(msg);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitDescriptionStatReplyState.class));
	}

	protected OFDescStatsReply createDescriptionStatsReply() {
		OFDescStatsReply statsReply = factory.buildDescStatsReply()
				.setDpDesc("Datapath Description")
				.setHwDesc("Hardware Description")
				.setMfrDesc("Manufacturer Description")
				.setSwDesc("Software Description")
				.setSerialNum("Serial Number")
				.build();
		return statsReply;
	}

	protected OFTableFeaturesStatsReply createTableFeaturesStatsReply() {
		OFTableFeaturesStatsReply statsReply = factory.buildTableFeaturesStatsReply()
				.setEntries(Collections.singletonList(factory.buildTableFeatures()
						.setConfig(0)
						.setMaxEntries(100)
						.setMetadataMatch(U64.NO_MASK)
						.setMetadataWrite(U64.NO_MASK)
						.setName("MyTable")
						.setTableId(TableId.of(1))
						.setProperties(Collections.singletonList((OFTableFeatureProp)factory.buildTableFeaturePropMatch()
								.setOxmIds(Collections.singletonList(U32.of(100)))
								.build())
								).build()
						)

						).build();
		return statsReply;
	}

	/**
	 * setup the expectations for the mock switch that are needed
	 * after the switch is instantiated in the WAIT_DESCRIPTION_STATS STATE
	 * Will reset the switch
	 * @throws CounterException
	 */
	protected void setupSwitchForInstantiationWithReset()
			throws Exception {
		reset(sw);
		sw.setFeaturesReply(featuresReply);
		expectLastCall().once();
	}

	/**
	 * Tests a situation where a switch returns a QUARANTINE result. This means
	 * we should move the handshake handler to a quarantine state and also
	 * quarantine the switch in the controller.
	 *
	 * @throws Exception
	 */
	@Test
	public void moveQuarantine() throws Exception {
		moveToWaitAppHandshakeState();

		reset(switchManager);
		switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.QUARANTINED);
		expectLastCall().once();
		replay(switchManager);

		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(WaitAppHandshakeState.class));
		WaitAppHandshakeState state = (WaitAppHandshakeState) switchHandler.getStateForTesting();
		assertThat(state.getCurrentPlugin(), CoreMatchers.<OFSwitchAppHandshakePlugin>equalTo(handshakePlugin));

		reset(sw);
		expect(sw.getStatus()).andReturn(SwitchStatus.HANDSHAKE);
		sw.setStatus(SwitchStatus.QUARANTINED);
		expectLastCall().once();
		replay(sw);

		PluginResult result = new PluginResult(PluginResultType.QUARANTINE, "test quarantine");
		handshakePlugin.exitPlugin(result);

		assertThat(switchHandler.getStateForTesting(),
				CoreMatchers.instanceOf(QuarantineState.class));
		verify(switchManager);
	}

	/**
	 * Tests a situation where a plugin returns a DISCONNECT result. This means
	 * we should disconnect the connection and the state should not change.
	 *
	 * @throws Exception
	 */
	@Test
	public void failedAppHandshake() throws Exception {
		moveToWaitAppHandshakeState();

		assertThat(switchHandler.getStateForTesting(),
				CoreMatchers.instanceOf(WaitAppHandshakeState.class));

		WaitAppHandshakeState state = (WaitAppHandshakeState) switchHandler.getStateForTesting();
		assertThat(state.getCurrentPlugin(), CoreMatchers.<OFSwitchAppHandshakePlugin>equalTo(handshakePlugin));

		PluginResult result = new PluginResult(PluginResultType.DISCONNECT);
		handshakePlugin.exitPlugin(result);

		assertThat(connection.isConnected(), equalTo(false));
	}


	@Test
	public void validAppHandshakePluginReason() throws Exception {
		try{
			new PluginResult(PluginResultType.QUARANTINE,"This should not cause an exception");
		}catch(IllegalStateException e) {
			fail("This should cause an illegal state exception");
		}
	}

	@Test
	public void invalidAppHandshakePluginReason() throws Exception {
		try{
			new PluginResult(PluginResultType.CONTINUE,"This should cause an exception");
			fail("This should cause an illegal state exception");
		}catch(IllegalStateException e) { /* Expected */ }

		try{
			new PluginResult(PluginResultType.DISCONNECT,"This should cause an exception");
			fail("This should cause an illegal state exception");
		}catch(IllegalStateException e) { /* Expected */ }
	}

	/**
	 * Move the channel from scratch to WAIT_INITIAL_ROLE state via
	 * WAIT_SWITCH_DRIVER_SUB_HANDSHAKE
	 * Does extensive testing for the WAIT_SWITCH_DRIVER_SUB_HANDSHAKE state
	 *
	 */
	@Test
	public void testSwitchDriverSubHandshake()
			throws Exception {
		moveToWaitSwitchDriverSubHandshake();

		//-------------------------------------------------
		//-------------------------------------------------
		// Send a message to the handler, it should be passed to the
		// switch's sub-handshake handling. After this message the
		// sub-handshake will be complete
		// FIXME:LOJI: With Andi's fix for a default Match object we won't
		// need to build/set this match object

		Match match = factory.buildMatch().build();
		OFMessage m = factory.buildFlowRemoved().setMatch(match).build();
		resetToStrict(sw);
		sw.processDriverHandshakeMessage(m);
		expectLastCall().once();
		expect(sw.isDriverHandshakeComplete()).andReturn(true).once();
		replay(sw);

		switchHandler.processOFMessage(m);

		assertThat(switchHandler.getStateForTesting(),
				CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitAppHandshakeState.class));
		assertThat("Unexpected message captured", connection.getMessages(), Matchers.empty());
		verify(sw);
	}

	@Test
	/** Test WaitDescriptionReplyState */
	public void testWaitDescriptionReplyState() throws Exception {
		moveToWaitInitialRole();
	}

	/**
	 * Setup the mock switch and write capture for a role request, set the
	 * role and verify mocks.
	 * @param supportsNxRole whether the switch supports role request messages
	 * to setup the attribute. This must be null (don't yet know if roles
	 * supported: send to check) or true.
	 * @param role The role to send
	 * @throws IOException
	 */
	private long setupSwitchSendRoleRequestAndVerify(Boolean supportsNxRole,
			OFControllerRole role) throws IOException {
		assertTrue("This internal test helper method most not be called " +
				"with supportsNxRole==false. Test setup broken",
				supportsNxRole == null || supportsNxRole == true);
		reset(sw);
		expect(sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
		.andReturn(supportsNxRole).atLeastOnce();
		replay(sw);

		switchHandler.sendRoleRequest(role);

		OFMessage msg = connection.retrieveMessage();
		verifyRoleRequest(msg, role);
		verify(sw);
		return msg.getXid();
	}


	/**
	 * Setup the mock switch for a role change request where the switch
	 * does not support roles.
	 *
	 * Needs to verify and reset the controller since we need to set
	 * an expectation
	 */
	@SuppressWarnings("unchecked")
	private void setupSwitchRoleChangeUnsupported(int xid,
			OFControllerRole role) {
		SwitchStatus newStatus = role != OFControllerRole.ROLE_SLAVE ? SwitchStatus.MASTER : SwitchStatus.SLAVE;
		boolean supportsNxRole = false;
		verify(switchManager);
		reset(sw, switchManager);
		expect(sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE))
		.andReturn(supportsNxRole).atLeastOnce();
		// TODO: hmmm. While it's not incorrect that we set the attribute
		// again it looks odd. Maybe change
		expect(sw.getOFFactory()).andReturn(factory).anyTimes();
		sw.write(anyObject(OFMessage.class));
		expectLastCall().anyTimes();
		sw.write(anyObject(Iterable.class));
		expectLastCall().anyTimes();
		expect(sw.getTables()).andStubReturn((short)0);
		sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, supportsNxRole);
		expectLastCall().anyTimes();
		if (SwitchStatus.MASTER == newStatus) {
			if (factory.getVersion().compareTo(OFVersion.OF_13) >= 0) {
				expect(sw.getMaxTableForTableMissFlow()).andReturn(TableId.ZERO).times(2);
			}
		}

		sw.setControllerRole(role);
		expectLastCall().once();

		if (role == OFControllerRole.ROLE_SLAVE) {
			sw.disconnect();
			expectLastCall().once();
		} else {
			expect(sw.getStatus()).andReturn(SwitchStatus.HANDSHAKE).once();
			sw.setStatus(newStatus);
			expectLastCall().once();
			switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, newStatus);
		}
		replay(sw, switchManager);

		switchHandler.sendRoleRequest(role);

		verify(sw, switchManager);
	}

	/** Return a bad request error message with the given xid/code */
	private OFMessage getBadRequestErrorMessage(OFBadRequestCode code, long xid) {
		OFErrorMsg msg = factory.errorMsgs().buildBadRequestErrorMsg()
				.setXid(xid)
				.setCode(code)
				.build();
		return msg;
	}

	/** Return a bad action error message with the given xid/code */
	private OFMessage getBadActionErrorMessage(OFBadActionCode code, long xid) {
		OFErrorMsg msg = factory.errorMsgs().buildBadActionErrorMsg()
				.setXid(xid)
				.setCode(code)
				.build();
		return msg;
	}


	/** Move the channel from scratch to MASTER state
	 * Builds on doMoveToWaitInitialRole()
	 * adds testing for WAIT_INITAL_ROLE state
	 *
	 * This method tests only the simple case that the switch supports roles
	 * and transitions to MASTER
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testInitialMoveToMasterWithRole() throws Exception {
		// first, move us to WAIT_INITIAL_ROLE_STATE
		moveToWaitInitialRole();
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// Set the role
		long xid = setupSwitchSendRoleRequestAndVerify(null, OFControllerRole.ROLE_MASTER);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// prepare mocks and inject the role reply message
		reset(sw);
		expect(sw.getOFFactory()).andReturn(factory).anyTimes();
		sw.write(anyObject(OFMessage.class));
		expectLastCall().anyTimes();
		sw.write(anyObject(Iterable.class));
		expectLastCall().anyTimes();
		expect(sw.getTables()).andStubReturn((short)0);
		sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
		expectLastCall().once();
		sw.setControllerRole(OFControllerRole.ROLE_MASTER);
		expectLastCall().once();
		expect(sw.getStatus()).andReturn(SwitchStatus.HANDSHAKE).once();
		sw.setStatus(SwitchStatus.MASTER);
		expectLastCall().once();
		if (factory.getVersion().compareTo(OFVersion.OF_13) >= 0) {
			expect(sw.getMaxTableForTableMissFlow()).andReturn(TableId.ZERO).times(2);
		}
		replay(sw);

		reset(switchManager);
		switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
		expectLastCall().once();
		replay(switchManager);
		OFMessage reply = getRoleReply(xid, OFControllerRole.ROLE_MASTER);

		// sendMessageToHandler will verify and rest controller mock
		switchHandler.processOFMessage(reply);

		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.MasterState.class));
	}

	/** Move the channel from scratch to SLAVE state
	 * Builds on doMoveToWaitInitialRole()
	 * adds testing for WAIT_INITAL_ROLE state
	 *
	 * This method tests only the simple case that the switch supports roles
	 * and transitions to SLAVE
	 */
	@Test
	public void testInitialMoveToSlaveWithRole() throws Exception {

		// first, move us to WAIT_INITIAL_ROLE_STATE
		moveToWaitInitialRole();
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// Set the role
		long xid = setupSwitchSendRoleRequestAndVerify(null, OFControllerRole.ROLE_SLAVE);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// prepare mocks and inject the role reply message
		reset(sw);
		sw.setAttribute(IOFSwitchBackend.SWITCH_SUPPORTS_NX_ROLE, true);
		expectLastCall().once();
		sw.setControllerRole(OFControllerRole.ROLE_SLAVE);
		expectLastCall().once();
		expect(sw.getStatus()).andReturn(SwitchStatus.HANDSHAKE).once();
		sw.setStatus(SwitchStatus.SLAVE);
		expectLastCall().once();
		replay(sw);

		reset(switchManager);
		switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.SLAVE);
		expectLastCall().once();
		replay(switchManager);

		OFMessage reply = getRoleReply(xid, OFControllerRole.ROLE_SLAVE);

		// sendMessageToHandler will verify and rest controller mock
		switchHandler.processOFMessage(reply);

		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.SlaveState.class));
	}

	/** Move the channel from scratch to MASTER state
	 * Builds on doMoveToWaitInitialRole()
	 * adds testing for WAIT_INITAL_ROLE state
	 *
	 * This method tests the case that the switch does NOT support roles.
	 * The channel handler still needs to send the initial request to find
	 * out that whether the switch supports roles.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testInitialMoveToMasterNoRole() throws Exception {
		// first, move us to WAIT_INITIAL_ROLE_STATE
		moveToWaitInitialRole();
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// Set the role
		long xid = setupSwitchSendRoleRequestAndVerify(null, OFControllerRole.ROLE_MASTER);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// prepare mocks and inject the role reply message
		reset(sw);
		expect(sw.getOFFactory()).andReturn(factory).anyTimes();
		sw.write(anyObject(OFMessage.class));
		expectLastCall().anyTimes();
		sw.write(anyObject(Iterable.class));
		expectLastCall().anyTimes();
		expect(sw.getTables()).andStubReturn((short)0);
		sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
		expectLastCall().once();
		sw.setControllerRole(OFControllerRole.ROLE_MASTER);
		expectLastCall().once();
		expect(sw.getStatus()).andReturn(SwitchStatus.HANDSHAKE).once();
		sw.setStatus(SwitchStatus.MASTER);
		expectLastCall().once();
		if (factory.getVersion().compareTo(OFVersion.OF_13) >= 0) {
			expect(sw.getMaxTableForTableMissFlow()).andReturn(TableId.ZERO).times(2);
		}
		replay(sw);

		// FIXME: shouldn't use ordinal(), but OFError is broken

		// Error with incorrect xid and type. Should be ignored.
		OFMessage err = getBadActionErrorMessage(OFBadActionCode.BAD_TYPE, xid+1);
		// sendMessageToHandler will verify and rest controller mock
		switchHandler.processOFMessage(err);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// Error with correct xid. Should trigger state transition
		err = getBadRequestErrorMessage(OFBadRequestCode.BAD_EXPERIMENTER, xid);
		reset(switchManager);
		switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
		expectLastCall().once();
		replay(switchManager);
		// sendMessageToHandler will verify and rest controller mock
		switchHandler.processOFMessage(err);

		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.MasterState.class));
	}

	/** Move the channel from scratch to MASTER state
	 * Builds on doMoveToWaitInitialRole()
	 * adds testing for WAIT_INITAL_ROLE state
	 *
	 * We let the initial role request time out. Role support should be
	 * disabled but the switch should be activated.
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testInitialMoveToMasterTimeout() throws Exception {
		int timeout = 50;
		switchHandler.useRoleChangerWithOtherTimeoutForTesting(timeout);

		// first, move us to WAIT_INITIAL_ROLE_STATE
		moveToWaitInitialRole();
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// Set the role
		setupSwitchSendRoleRequestAndVerify(null, OFControllerRole.ROLE_MASTER);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// prepare mocks and inject the role reply message
		reset(sw);
		expect(sw.getOFFactory()).andReturn(factory).anyTimes();
		sw.write(anyObject(OFMessage.class));
		expectLastCall().anyTimes();
		sw.write(anyObject(Iterable.class));
		expectLastCall().anyTimes();
		expect(sw.getTables()).andStubReturn((short)0);
		sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
		expectLastCall().once();
		sw.setControllerRole(OFControllerRole.ROLE_MASTER);
		expectLastCall().once();
		expect(sw.getStatus()).andReturn(SwitchStatus.HANDSHAKE).once();
		sw.setStatus(SwitchStatus.MASTER);
		expectLastCall().once();
		if (factory.getVersion().compareTo(OFVersion.OF_13) >= 0) {
			expect(sw.getMaxTableForTableMissFlow()).andReturn(TableId.ZERO).times(2);
		}
		replay(sw);

		OFMessage m = factory.buildBarrierReply().build();

		Thread.sleep(timeout+5);

		reset(switchManager);
		switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
		expectLastCall().once();
		replay(switchManager);
		switchHandler.processOFMessage(m);

		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.MasterState.class));

	}


	/** Move the channel from scratch to SLAVE state
	 * Builds on doMoveToWaitInitialRole()
	 * adds testing for WAIT_INITAL_ROLE state
	 *
	 * This method tests the case that the switch does NOT support roles.
	 * The channel handler still needs to send the initial request to find
	 * out that whether the switch supports roles.
	 *
	 */
	@Test
	public void testInitialMoveToSlaveNoRole() throws Exception {
		// first, move us to WAIT_INITIAL_ROLE_STATE
		moveToWaitInitialRole();
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// Set the role
		long xid = setupSwitchSendRoleRequestAndVerify(null, OFControllerRole.ROLE_SLAVE);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// prepare mocks and inject the role reply message
		reset(sw);
		sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
		expectLastCall().once();
		sw.setControllerRole(OFControllerRole.ROLE_SLAVE);
		expectLastCall().once();
		sw.disconnect(); // Make sure we disconnect
		expectLastCall().once();
		replay(sw);


		// FIXME: shouldn't use ordinal(), but OFError is broken

		// Error with incorrect xid and type. Should be ignored.
		OFMessage err = getBadActionErrorMessage(OFBadActionCode.BAD_TYPE, xid+1);

		// sendMessageToHandler will verify and rest controller mock
		switchHandler.processOFMessage(err);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// Error with correct xid. Should trigger state transition
		err = getBadRequestErrorMessage(OFBadRequestCode.BAD_EXPERIMENTER, xid);
		// sendMessageToHandler will verify and rest controller mock
		switchHandler.processOFMessage(err);
	}


	/** Move the channel from scratch to SLAVE state
	 * Builds on doMoveToWaitInitialRole()
	 * adds testing for WAIT_INITAL_ROLE state
	 *
	 * We let the initial role request time out. The switch should be
	 * disconnected
	 */
	@Test
	public void testInitialMoveToSlaveTimeout() throws Exception {
		int timeout = 50;
		switchHandler.useRoleChangerWithOtherTimeoutForTesting(timeout);

		// first, move us to WAIT_INITIAL_ROLE_STATE
		moveToWaitInitialRole();
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// Set the role
		setupSwitchSendRoleRequestAndVerify(null, OFControllerRole.ROLE_SLAVE);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// prepare mocks and inject the role reply message
		reset(sw);
		sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, false);
		expectLastCall().once();
		sw.setControllerRole(OFControllerRole.ROLE_SLAVE);
		expectLastCall().once();
		sw.disconnect(); // Make sure we disconnect
		expectLastCall().once();
		replay(sw);

		// Apparently this can be any type of message for this test?!
		OFMessage m = factory.buildBarrierReply().build();

		Thread.sleep(timeout+5);
		switchHandler.processOFMessage(m);
	}


	/** Move channel from scratch to WAIT_INITIAL_STATE, then MASTER,
	 * then SLAVE for cases where the switch does not support roles.
	 * I.e., the final SLAVE transition should disconnect the switch.
	 */
	@Test
	public void testNoRoleInitialToMasterToSlave() throws Exception {
		int xid = 46;
		// First, lets move the state to MASTER without role support
		testInitialMoveToMasterNoRole();
		assertThat(switchHandler.getStateForTesting(),
				CoreMatchers.instanceOf(OFSwitchHandshakeHandler.MasterState.class));

		assertThat("Unexpected messages have been captured",
				connection.getMessages(),
				Matchers.empty());

		// try to set master role again. should be a no-op
		setupSwitchRoleChangeUnsupported(xid, OFControllerRole.ROLE_MASTER);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.MasterState.class));

		assertThat("Unexpected messages have been captured",
				connection.getMessages(),
				Matchers.empty());

		setupSwitchRoleChangeUnsupported(xid, OFControllerRole.ROLE_SLAVE);
		assertThat(connection.isConnected(), equalTo(false));

		assertThat("Unexpected messages have been captured",
				connection.getMessages(),
				Matchers.empty());
	}

	/** Move the channel to MASTER state
	 * Expects that the channel is in MASTER or SLAVE state.
	 *
	 */
	@SuppressWarnings("unchecked")
	public void changeRoleToMasterWithRequest() throws Exception {
		assertTrue("This method can only be called when handler is in " +
				"MASTER or SLAVE role", switchHandler.isHandshakeComplete());

		// Set the role
		long xid = setupSwitchSendRoleRequestAndVerify(true, OFControllerRole.ROLE_MASTER);

		// prepare mocks and inject the role reply message
		reset(sw);
		expect(sw.getOFFactory()).andReturn(factory).anyTimes();
		sw.write(anyObject(OFMessage.class));
		expectLastCall().anyTimes();
		sw.write(anyObject(Iterable.class));
		expectLastCall().anyTimes();
		expect(sw.getTables()).andStubReturn((short)0);
		sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
		expectLastCall().once();
		sw.setControllerRole(OFControllerRole.ROLE_MASTER);
		expectLastCall().once();
		expect(sw.getStatus()).andReturn(SwitchStatus.HANDSHAKE).once();
		sw.setStatus(SwitchStatus.MASTER);
		expectLastCall().once();
		expect(sw.getMaxTableForTableMissFlow()).andReturn(TableId.ZERO).times(2);
		replay(sw);

		reset(switchManager);
		switchManager.switchStatusChanged(sw, SwitchStatus.HANDSHAKE, SwitchStatus.MASTER);
		expectLastCall().once();
		replay(switchManager);

		OFMessage reply = getRoleReply(xid, OFControllerRole.ROLE_MASTER);

		switchHandler.processOFMessage(reply);

		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.MasterState.class));
	}

	/** Move the channel to SLAVE state
	 * Expects that the channel is in MASTER or SLAVE state.
	 *
	 */
	public void changeRoleToSlaveWithRequest() throws Exception {
		assertTrue("This method can only be called when handler is in " +
				"MASTER or SLAVE role", switchHandler.isHandshakeComplete());

		// Set the role
		long xid = setupSwitchSendRoleRequestAndVerify(true, OFControllerRole.ROLE_SLAVE);

		// prepare mocks and inject the role reply message
		reset(sw);
		sw.setAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, true);
		expectLastCall().once();
		sw.setControllerRole(OFControllerRole.ROLE_SLAVE);
		expectLastCall().once();
		expect(sw.getStatus()).andReturn(SwitchStatus.MASTER).once();
		sw.setStatus(SwitchStatus.SLAVE);
		expectLastCall().once();
		replay(sw);

		reset(switchManager);
		switchManager.switchStatusChanged(sw, SwitchStatus.MASTER, SwitchStatus.SLAVE);
		expectLastCall().once();
		replay(switchManager);

		OFMessage reply = getRoleReply(xid, OFControllerRole.ROLE_SLAVE);
		connection.getListener().messageReceived(connection, reply);

		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.SlaveState.class));
	}

	@Test
	public void testMultiRoleChange1() throws Exception {
		testInitialMoveToMasterWithRole();
		changeRoleToMasterWithRequest();
		changeRoleToSlaveWithRequest();
		changeRoleToSlaveWithRequest();
		changeRoleToMasterWithRequest();
		changeRoleToSlaveWithRequest();
	}

	@Test
	public void testMultiRoleChange2() throws Exception {
		testInitialMoveToSlaveWithRole();
		changeRoleToMasterWithRequest();
		changeRoleToSlaveWithRequest();
		changeRoleToSlaveWithRequest();
		changeRoleToMasterWithRequest();
		changeRoleToSlaveWithRequest();
	}

	/** Start from scratch and reply with an unexpected error to the role
	 * change request
	 * Builds on doMoveToWaitInitialRole()
	 * adds testing for WAIT_INITAL_ROLE state
	 */
	@Test
	public void testInitialRoleChangeOtherError() throws Exception {
		// first, move us to WAIT_INITIAL_ROLE_STATE
		moveToWaitInitialRole();
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		// Set the role
		long xid = setupSwitchSendRoleRequestAndVerify(null, OFControllerRole.ROLE_MASTER);
		assertThat(switchHandler.getStateForTesting(), CoreMatchers.instanceOf(OFSwitchHandshakeHandler.WaitInitialRoleState.class));

		OFMessage err = getBadActionErrorMessage(OFBadActionCode.BAD_TYPE, xid);

		verifyExceptionCaptured(err, SwitchStateException.class);
	}

	/**
	 * Test dispatch of messages while in MASTER role
	 */
	@Test
	public void testMessageDispatchMaster() throws Exception {
		testInitialMoveToMasterWithRole();

		// Send packet in. expect dispatch
		OFPacketIn pi = factory.buildPacketIn()
				.setReason(OFPacketInReason.NO_MATCH)
				.build();
		reset(switchManager);
		switchManager.handleMessage(sw, pi, null);
		expectLastCall().once();
		replay(switchManager);
		switchHandler.processOFMessage(pi);

		// TODO: many more to go
	}

	/**
	 * Test port status message handling while MASTER
	 *
	 */
	@Test
	public void testPortStatusMessageMaster() throws Exception {
		DatapathId dpid = featuresReply.getDatapathId();
		testInitialMoveToMasterWithRole();

		OFPortDesc portDesc = factory.buildPortDesc()
				.setName("Port1")
				.setPortNo(OFPort.of(1))
				.build();
		OFPortStatus.Builder portStatusBuilder = factory.buildPortStatus()
				.setDesc(portDesc);

		// The events we expect sw.handlePortStatus to return
		// We'll just use the same list for all valid OFPortReasons and add
		// arbitrary events for arbitrary ports that are not necessarily
		// related to the port status message. Our goal
		// here is not to return the correct set of events but the make sure
		// that a) sw.handlePortStatus is called
		//      b) the list of events sw.handlePortStatus returns is sent
		//         as IOFSwitchListener notifications.
		OrderedCollection<PortChangeEvent> events =
				new LinkedHashSetWrapper<PortChangeEvent>();
		OFPortDesc.Builder pb = factory.buildPortDesc();
		OFPortDesc p1 = pb.setName("eth1").setPortNo(OFPort.of(1)).build();
		OFPortDesc p2 = pb.setName("eth2").setPortNo(OFPort.of(2)).build();
		OFPortDesc p3 = pb.setName("eth3").setPortNo(OFPort.of(3)).build();
		OFPortDesc p4 = pb.setName("eth4").setPortNo(OFPort.of(4)).build();
		OFPortDesc p5 = pb.setName("eth5").setPortNo(OFPort.of(5)).build();


		events.add(new PortChangeEvent(p1, PortChangeType.ADD));
		events.add(new PortChangeEvent(p2, PortChangeType.DELETE));
		events.add(new PortChangeEvent(p3, PortChangeType.UP));
		events.add(new PortChangeEvent(p4, PortChangeType.DOWN));
		events.add(new PortChangeEvent(p5, PortChangeType.OTHER_UPDATE));


		for (OFPortReason reason: OFPortReason.values()) {
			OFPortStatus portStatus = portStatusBuilder.setReason(reason).build();

			reset(sw);
			expect(sw.getId()).andReturn(dpid).anyTimes();

			expect(sw.processOFPortStatus(portStatus)).andReturn(events).once();
			replay(sw);

			reset(switchManager);
			switchManager.notifyPortChanged(sw, p1, PortChangeType.ADD);
			switchManager.notifyPortChanged(sw, p2, PortChangeType.DELETE);
			switchManager.notifyPortChanged(sw, p3, PortChangeType.UP);
			switchManager.notifyPortChanged(sw, p4, PortChangeType.DOWN);
			switchManager.notifyPortChanged(sw, p5, PortChangeType.OTHER_UPDATE);
			replay(switchManager);

			switchHandler.processOFMessage(portStatus);

			verify(sw);
		}
	}

	/**
	 * Test re-assert MASTER
	 *
	 */
	@Test
	public void testReassertMaster() throws Exception {
		testInitialMoveToMasterWithRole();

		OFMessage err = getBadRequestErrorMessage(OFBadRequestCode.EPERM, 42);

		reset(roleManager);
		roleManager.reassertRole(switchHandler, HARole.ACTIVE);
		expectLastCall().once();
		replay(roleManager);

		reset(switchManager);
		switchManager.handleMessage(sw, err, null);
		expectLastCall().once();
		replay(switchManager);

		switchHandler.processOFMessage(err);

		verify(sw);
	}

	/**
	 * Verify that the given exception event capture (as returned by
	 * getAndInitExceptionCapture) has thrown an exception of the given
	 * expectedExceptionClass.
	 * Resets the capture
	 * @param err
	 */
	void verifyExceptionCaptured(
			OFMessage err, Class<? extends Throwable> expectedExceptionClass) {

		Throwable caughtEx = null;
		// This should purposely cause an exception
		try{
			switchHandler.processOFMessage(err);
		}
		catch(Exception e){
			// Capture the exception
			caughtEx = e;
		}

		assertThat(caughtEx, CoreMatchers.instanceOf(expectedExceptionClass));
	}

	/**
	 * Tests the connection closed functionality before the switch handshake is complete.
	 * Essentially when the switch handshake is only aware of the IOFConnection.
	 */
	@Test
	public void testConnectionClosedBeforeHandshakeComplete() {

		// Test connection closed prior to being finished
		reset(switchManager);
		switchManager.handshakeDisconnected(dpid);
		expectLastCall().once();
		replay(switchManager);

		switchHandler.connectionClosed(connection);

		verify(switchManager);
	}

	/**
	 * Tests the connection closed functionality after the switch handshake is complete.
	 * Essentially when the switch handshake is aware of an IOFSwitch.
	 * @throws Exception
	 */
	@Test
	public void testConnectionClosedAfterHandshakeComplete() throws Exception {

		testInitialMoveToMasterWithRole();
		// Test connection closed prior to being finished
		reset(switchManager);
		switchManager.handshakeDisconnected(dpid);
		expectLastCall().once();
		switchManager.switchDisconnected(sw);
		expectLastCall().once();
		replay(switchManager);

		reset(sw);
		expect(sw.getStatus()).andReturn(SwitchStatus.DISCONNECTED).anyTimes();
		replay(sw);

		switchHandler.connectionClosed(connection);

		verify(switchManager);
		verify(sw);
	}
}
