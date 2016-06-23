package net.floodlightcontroller.routing;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.test.MockSyncService;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;
import net.floodlightcontroller.debugevent.IDebugEventService;
import net.floodlightcontroller.debugevent.MockDebugEventService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClassifierService;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.test.MockDeviceManager;
import net.floodlightcontroller.forwarding.Forwarding;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.internal.LinkDiscoveryManager;
import net.floodlightcontroller.test.FloodlightTestCase;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyShort;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertSame;

public class MultipathRoutingTest extends FloodlightTestCase {
	
	protected FloodlightContext cntx;
	protected MockDeviceManager deviceManager;
	protected IRoutingService routingEngine;
	protected Forwarding forwarding;
	protected ITopologyService topology;
	protected LinkDiscoveryManager linkService;
	protected MockThreadPoolService threadPool;
	protected IOFSwitch sw1, sw2, sw3, sw4;
	protected OFFeaturesReply swFeatures;
	protected OFDescStatsReply swDescription;
	protected Date currentDate;
	private MockSyncService mockSyncService;
	private OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
	
	
	@Override
	public void setUp() throws Exception {
		super.setUp();

		cntx = new FloodlightContext();

		// Module loader setup
		moduleLoaderSetup();

		DefaultEntityClassifier entityClassifier = new DefaultEntityClassifier();
		FloodlightModuleContext fmc = new FloodlightModuleContext();
		addServicesToFloodlightModuleContext(entityClassifier, fmc);

		topology.addListener(anyObject(ITopologyListener.class));

		expectLastCall().anyTimes();
		expect(topology.isIncomingBroadcastAllowed(anyObject(DatapathId.class), anyObject(OFPort.class))).andReturn(true).anyTimes();
		replay(topology);

		initMockUps(entityClassifier, fmc);
		verify(topology);

		swDescription = factory.buildDescStatsReply().build();
		swFeatures = factory.buildFeaturesReply().setNBuffers(1000).build();
		// Mock switches
		mockSwitches();

		// Load the switch map
		Map<DatapathId, IOFSwitch> switches = new HashMap<DatapathId, IOFSwitch>();
		switches.put(DatapathId.of(1L), sw1);
		switches.put(DatapathId.of(2L), sw2);
		switches.put(DatapathId.of(1L), sw3);
		switches.put(DatapathId.of(2L), sw4);
		getMockSwitchService().setSwitches(switches);

		currentDate = new Date();

	}

	@Test
	public void testForwardMultipath() throws Exception {

		ArrayList<Route> routesList = new ArrayList<Route>();

		Route route = new Route(DatapathId.of(1L), DatapathId.of(2L));
		route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(1)));
		route.getPath().add(new NodePortTuple(DatapathId.of(2L), OFPort.of(1)));
		routesList.add(route);

		route = new Route(DatapathId.of(1L), DatapathId.of(2L));
		route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(2)));
		route.getPath().add(new NodePortTuple(DatapathId.of(3L), OFPort.of(1)));
		route.getPath().add(new NodePortTuple(DatapathId.of(3L), OFPort.of(2)));
		route.getPath().add(new NodePortTuple(DatapathId.of(2L), OFPort.of(2)));
		routesList.add(route);

		route = new Route(DatapathId.of(1L), DatapathId.of(2L));
		route.getPath().add(new NodePortTuple(DatapathId.of(1L), OFPort.of(3)));
		route.getPath().add(new NodePortTuple(DatapathId.of(4L), OFPort.of(1)));
		route.getPath().add(new NodePortTuple(DatapathId.of(4L), OFPort.of(2)));
		route.getPath().add(new NodePortTuple(DatapathId.of(2L), OFPort.of(3)));
		routesList.add(route);

		//Setting required for new functionality
		ForwardingBase.USE_MULTIPATH_ROUTING = true;
		expect(routingEngine.getRoutes(DatapathId.of(1L), DatapathId.of(2L), false)).andReturn(routesList).anyTimes();

		reset(topology);
		expect(topology.getOpenflowDomainId(DatapathId.of(1L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.getOpenflowDomainId(DatapathId.of(2L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.getOpenflowDomainId(DatapathId.of(3L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.getOpenflowDomainId(DatapathId.of(4L))).andReturn(DatapathId.of(1L)).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(2L),  OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(2))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(3L),  OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(1L),  OFPort.of(3))).andReturn(true).anyTimes();
		expect(topology.isAttachmentPointPort(DatapathId.of(4L),  OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isIncomingBroadcastAllowed(DatapathId.of(anyLong()), OFPort.of(anyShort()))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(2L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(2))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(3L), OFPort.of(1))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(1L), OFPort.of(3))).andReturn(true).anyTimes();
		expect(topology.isEdge(DatapathId.of(4L), OFPort.of(1))).andReturn(true).anyTimes();

		// Reset mocks, trigger the packet in, and validate results
		replay(sw1, sw2, sw3, sw4, routingEngine, topology);
//		forwarding.receive(sw1, this.packetIn, cntx);
		verify(sw1, sw2, sw3, sw4, routingEngine);

		assertSame(routingEngine.getRoutes(DatapathId.of(1L), DatapathId.of(2L), false), routesList);  // wc1 should get packetout + flowmod.

	}

	private void moduleLoaderSetup() {
		mockFloodlightProvider = getMockFloodlightProvider();
		forwarding = new Forwarding();
		threadPool = new MockThreadPoolService();
		deviceManager = new MockDeviceManager();
		routingEngine = createMock(IRoutingService.class);
		topology = createMock(ITopologyService.class);
		mockSyncService = new MockSyncService();
		linkService = new LinkDiscoveryManager();
	}

	private void addServicesToFloodlightModuleContext(DefaultEntityClassifier entityClassifier, FloodlightModuleContext fmc) {
		fmc.addService(IFloodlightProviderService.class,
				mockFloodlightProvider);
		fmc.addService(IThreadPoolService.class, threadPool);
		fmc.addService(ITopologyService.class, topology);
		fmc.addService(IRoutingService.class, routingEngine);
		fmc.addService(IDeviceService.class, deviceManager);
		fmc.addService(IEntityClassifierService.class, entityClassifier);
		fmc.addService(ISyncService.class, mockSyncService);
		fmc.addService(IDebugCounterService.class, new MockDebugCounterService());
		fmc.addService(IDebugEventService.class, new MockDebugEventService());
		fmc.addService(IOFSwitchService.class, getMockSwitchService());
		fmc.addService(ILinkDiscoveryService.class, linkService);
	}

	private void assertSwitchesConfiguration() {
		expect(sw1.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
		expect(sw2.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
		expect(sw3.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
		expect(sw4.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();

		expect(sw1.getSwitchDescription()).andReturn(new SwitchDescription(swDescription)).anyTimes();
		expect(sw2.getSwitchDescription()).andReturn(new SwitchDescription(swDescription)).anyTimes();
		expect(sw3.getSwitchDescription()).andReturn(new SwitchDescription(swDescription)).anyTimes();
		expect(sw4.getSwitchDescription()).andReturn(new SwitchDescription(swDescription)).anyTimes();

		expect(sw1.isActive()).andReturn(true).anyTimes();
		expect(sw2.isActive()).andReturn(true).anyTimes();
		expect(sw3.isActive()).andReturn(true).anyTimes();
		expect(sw4.isActive()).andReturn(true).anyTimes();
	}

	private void mockSwitches() {
		sw1 = EasyMock.createMock(IOFSwitch.class);
		expect(sw1.getId()).andReturn(DatapathId.of(1L)).anyTimes();
		expect(sw1.getOFFactory()).andReturn(factory).anyTimes();
		expect(sw1.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();

		sw2 = EasyMock.createMock(IOFSwitch.class);
		expect(sw2.getId()).andReturn(DatapathId.of(2L)).anyTimes();
		expect(sw2.getOFFactory()).andReturn(factory).anyTimes();
		expect(sw2.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();

		sw3 = EasyMock.createMock(IOFSwitch.class);
		expect(sw3.getId()).andReturn(DatapathId.of(1L)).anyTimes();
		expect(sw3.getOFFactory()).andReturn(factory).anyTimes();
		expect(sw3.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();

		sw4 = EasyMock.createMock(IOFSwitch.class);
		expect(sw4.getId()).andReturn(DatapathId.of(1L)).anyTimes();
		expect(sw4.getOFFactory()).andReturn(factory).anyTimes();
		expect(sw4.getBuffers()).andReturn(swFeatures.getNBuffers()).anyTimes();

		assertSwitchesConfiguration();
	}

	private void initMockUps(DefaultEntityClassifier entityClassifier, FloodlightModuleContext fmc) throws FloodlightModuleException {
		threadPool.init(fmc);
		mockSyncService.init(fmc);
		linkService.init(fmc);
		deviceManager.init(fmc);
		forwarding.init(fmc);
		entityClassifier.init(fmc);
		threadPool.startUp(fmc);
		mockSyncService.startUp(fmc);
		linkService.startUp(fmc);
		deviceManager.startUp(fmc);
		forwarding.startUp(fmc);
		entityClassifier.startUp(fmc);
	}

}
