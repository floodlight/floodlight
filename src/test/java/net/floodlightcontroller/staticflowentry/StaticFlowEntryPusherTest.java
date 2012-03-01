package net.floodlightcontroller.staticflowentry;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.util.HexString;

public class StaticFlowEntryPusherTest extends FloodlightTestCase {
    String flowMod1, flowMod2;
    static String TestSwitch1DPID = "00:00:00:00:00:00:00:01";
    StaticFlowEntryPusher staticFlowEntryPusher;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        flowMod1 = "{\"switch\": \"00:00:00:00:00:00:00:01\", " +
                   "\"name\": \"flow-mod-1\", " +
                   "\"cookie\": \"0\", " +
                   "\"priority\": \"32768\", " +
                   "\"ingress-port\": \"1\"," +
                   "\"active\": \"true\", " +
                   "\"actions\": \"output=3\"}";
        
        flowMod2 = "{\"switch\": \"00:00:00:00:00:00:00:01\", " +
                   "\"name\": \"flow-mod-2\", " +
                   "\"cookie\": \"0\", " +
                   "\"priority\": \"32768\", " +
                   "\"ingress-port\": \"2\"," +
                   "\"active\": \"true\", " +
                   "\"actions\": \"output=3\"}";

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class, getMockFloodlightProvider());
        RestApiServer restApi = new RestApiServer();
        fmc.addService(IRestApiService.class, restApi);
        staticFlowEntryPusher = new StaticFlowEntryPusher();
        staticFlowEntryPusher.init(fmc);
        restApi.init(fmc);
        staticFlowEntryPusher.setFlowPushTime(200);
        staticFlowEntryPusher.startUp(fmc);
        restApi.startUp(fmc);
    }
    
    @Test
    public void testAddAndRemoveEntries() throws Exception {
        
        IOFSwitch mockSwitch = createMock(IOFSwitch.class);
        long dpid = HexString.toLong(TestSwitch1DPID);
        Capture<OFMessage> writeCapture = new Capture<OFMessage>(CaptureType.ALL);
        Capture<FloodlightContext> contextCapture = 
                new Capture<FloodlightContext>(CaptureType.ALL);
        Capture<List<OFMessage>> writeCaptureList = 
                new Capture<List<OFMessage>>(CaptureType.ALL);
        
        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();
        mockSwitch.write(capture(writeCaptureList), capture(contextCapture));
        expectLastCall().anyTimes();
        mockSwitch.flush();
        expectLastCall().anyTimes();
        
        MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();
        Map<Long, IOFSwitch> switchMap = new HashMap<Long, IOFSwitch>();
        switchMap.put(dpid, mockSwitch);
        mockFloodlightProvider.setSwitches(switchMap);
        // if someone calls getId(), return this dpid instead
        expect(mockSwitch.getId()).andReturn(dpid).anyTimes();
        replay(mockSwitch);
        
        // hook the static pusher up to the fake switch
        staticFlowEntryPusher.addedSwitch(mockSwitch);
        
        verify(mockSwitch);
        
        // Add entries
        staticFlowEntryPusher.addEntry(flowMod1);
        staticFlowEntryPusher.addEntry(flowMod2);
        
        // Verify that the switch has gotten some flow_mods
        int oldsize = writeCapture.getValues().size();
        assertTrue(oldsize >= 2);

        int count = 5;
        while (count >= 0) {
            Thread.sleep(staticFlowEntryPusher.getFlowPushTime());

            if (writeCapture.getValues().size() >= (2 + oldsize)) {
                break;
            }

            count -= 1;
        }
        int newsize = writeCapture.getValues().size();
        // Make sure the entries were pushed again
        assertTrue(newsize >= (2+oldsize));
        
        // Remove entries
        staticFlowEntryPusher.removeEntry(flowMod1);
        staticFlowEntryPusher.removeEntry(flowMod2);
        
        Thread.sleep(staticFlowEntryPusher.getFlowPushTime());
        // Make sure the entries were NOT pushed again
        assertEquals(newsize, writeCapture.getValues().size());
    }
}
