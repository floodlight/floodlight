package net.floodlightcontroller.debugcounter;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import net.floodlightcontroller.core.OFConnectionCounters;

import org.projectfloodlight.openflow.protocol.OFAsyncGetReply;
import org.projectfloodlight.openflow.protocol.OFAsyncGetRequest;
import org.projectfloodlight.openflow.protocol.OFAsyncSet;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFBundleAddMsg;
import org.projectfloodlight.openflow.protocol.OFBundleCtrlMsg;
import org.projectfloodlight.openflow.protocol.OFBundleCtrlType;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFControllerRoleReason;
import org.projectfloodlight.openflow.protocol.OFEchoReply;
import org.projectfloodlight.openflow.protocol.OFEchoRequest;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFExperimenter;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFFeaturesRequest;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFGetConfigRequest;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.protocol.OFHello;
import org.projectfloodlight.openflow.protocol.OFHelloElem;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFPortMod;
import org.projectfloodlight.openflow.protocol.OFPortReason;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigRequest;
import org.projectfloodlight.openflow.protocol.OFRequestforward;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.protocol.OFRoleRequest;
import org.projectfloodlight.openflow.protocol.OFRoleStatus;
import org.projectfloodlight.openflow.protocol.OFSetConfig;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFTableMod;
import org.projectfloodlight.openflow.protocol.OFTableReason;
import org.projectfloodlight.openflow.protocol.OFTableStatus;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.BundleId;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;

import net.floodlightcontroller.test.FloodlightTestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OFConnectionCountersTest extends FloodlightTestCase {
    private OFConnectionCounters counters ;
    private DebugCounterServiceImpl dc;
    private OFAuxId auxId;
    private DatapathId dpId;
    private OFFactory factory;

    protected static Logger log = LoggerFactory.getLogger(OFConnectionCountersTest.class);

    @Override
    @Before
    public void setUp() throws Exception {
        dc = new DebugCounterServiceImpl();
        dc.registerModule(OFConnectionCounters.COUNTER_MODULE);
        auxId = OFAuxId.of(5);
        dpId = DatapathId.of(5);
        counters = new OFConnectionCounters(dc, dpId, auxId);
    }

    @Test
    public void TestConnectionCounterRegistered(){
        for(OFType oft : OFType.values()){
            boolean found = false;
            for(DebugCounterResource dcInfo : dc.getAllCounterValues()){
                if(dcInfo.getCounterHierarchy().contains(oft.toString())){
                    found = true;
                }
            }
            assertEquals(true, found);
        }
    }

    /**
     * Check that only two counters are updated and rest are zero
     * @param typeStr Type of message as a string
     * @param value Expected value of the counter
     */
    public void validateCounter(String typeStr,long value){
        for(DebugCounterResource dcInfo : dc.getAllCounterValues()){
            if(dcInfo.getCounterHierarchy().contains("/"+typeStr)){
                assertEquals( Long.valueOf(value), dcInfo.getCounterValue());
            }
        }
    }
    /**
     * Sends two messages of read and write for the given type and tests
     * the counters
     * @param ofm
     * @param type
     */
    public void updateAndTestCounter(OFMessage ofm, String type){

        validateCounter(type, 0);
        counters.updateWriteStats(ofm);
        counters.updateWriteStats(ofm);

        counters.updateReadStats(ofm);
        counters.updateReadStats(ofm);
        validateCounter(type, 2);
    }

    @Test
    public void TestConnectionCounterUpdate() {
        // Barrier_Reply
        //
        factory = OFFactories.getFactory(OFVersion.OF_13);
        OFBarrierReply testMessage;
        testMessage = factory.barrierReply();
        updateAndTestCounter(testMessage, OFType.BARRIER_REPLY.toString() );

        //Barrier_Request
        //
        OFBarrierRequest brMsg = factory.barrierRequest();
        updateAndTestCounter(brMsg,OFType.BARRIER_REQUEST.toString());


        //Echo Reply
        //
        OFEchoReply echorepMsg = factory.echoReply(new byte[1]);
        updateAndTestCounter(echorepMsg,OFType.ECHO_REPLY.toString());


        // Echo Request
        //
        OFEchoRequest echoreqMsg = factory.echoRequest(new byte[1]);
        updateAndTestCounter(echoreqMsg,OFType.ECHO_REQUEST.toString());

        // Error
        //
        OFErrorMsg errMsg = factory.errorMsgs().buildBadRequestErrorMsg()
                .setXid(Long.valueOf(2))
                .setCode(OFBadRequestCode.BAD_PORT)
                .build();
        updateAndTestCounter(errMsg,OFType.ERROR.toString());

        //Experimenter
        //
        OFExperimenter expMsg = factory.bsnGetMirroringRequest((short) 0);
        updateAndTestCounter(expMsg,OFType.EXPERIMENTER.toString());

        // Features Request
        //
        OFFeaturesRequest featuresRequest = factory.featuresRequest();
        updateAndTestCounter(featuresRequest, OFType.FEATURES_REQUEST.toString());

        // Features Reply
        //
        OFFeaturesReply  featuresReplyMsg = factory.buildFeaturesReply().build();
        updateAndTestCounter(featuresReplyMsg,OFType.FEATURES_REPLY.toString());


        //Flow Mod
        //
        OFFlowMod flowModMsg = factory.buildFlowModify().build();
        updateAndTestCounter(flowModMsg,OFType.FLOW_MOD.toString());

        //Flow Removed
        //
        OFFlowRemoved flowRemMsg = factory.buildFlowRemoved().build();
        updateAndTestCounter(flowRemMsg,OFType.FLOW_REMOVED.toString());


        //Get Async Reply
        //
        OFAsyncGetReply getAsyncReplMsg = factory.buildAsyncGetReply().build();
        updateAndTestCounter(getAsyncReplMsg,OFType.GET_ASYNC_REPLY.toString());

        //Get Async Req
        //
        OFAsyncGetRequest getAsyncReqMsg = factory.buildAsyncGetRequest().build();
        updateAndTestCounter(getAsyncReqMsg,OFType.GET_ASYNC_REQUEST.toString());

        // config reply negative
        //
        validateCounter(OFType.GET_CONFIG_REPLY.toString(), 0);

        // Get config request
        //
        OFGetConfigRequest getConfReqMsg = factory.getConfigRequest();
        updateAndTestCounter(getConfReqMsg,OFType.GET_CONFIG_REQUEST.toString());

        // Get config reply
        //
        OFGetConfigReply getConfigReplyMsg = factory.buildGetConfigReply().build();
        updateAndTestCounter(getConfigReplyMsg,OFType.GET_CONFIG_REPLY.toString());

        // Group mod
        //
        OFGroupMod groupModMsg =  factory.buildGroupModify()
                .setGroup(OFGroup.ZERO)
                .setGroupType(OFGroupType.INDIRECT).build();
        updateAndTestCounter(groupModMsg,OFType.GROUP_MOD.toString());

        // Hello
        //
        OFHello helloMsg = factory.hello(new ArrayList<OFHelloElem>());
        updateAndTestCounter(helloMsg,OFType.HELLO.toString());

        // meter mod
        //
        OFMeterMod meterModMsg = factory.buildMeterMod().build();
        updateAndTestCounter(meterModMsg,OFType.METER_MOD.toString());

        // Packet in
        //
        OFPacketIn pktInMsg = factory.buildPacketIn().setReason(OFPacketInReason.ACTION).build();
        updateAndTestCounter(pktInMsg,OFType.PACKET_IN.toString());

        // Packet out
        //
        OFPacketOut pktOutMsg = factory.buildPacketOut().build();
        updateAndTestCounter(pktOutMsg,OFType.PACKET_OUT.toString());

        // Port Mod
        //
        OFPortMod portMod = factory.buildPortMod().build();
        updateAndTestCounter(portMod,OFType.PORT_MOD.toString());

        // Port Status
        //
        OFPortDesc portDesc =  factory.buildPortDesc()
                .setName("Eth1")
                .setPortNo(OFPort.of(1))
                .build();
        OFPortStatus portStatus = factory.buildPortStatus().setXid(0).setDesc(portDesc).setReason(OFPortReason.ADD).build();
        updateAndTestCounter(portStatus,OFType.PORT_STATUS.toString());

        // Queue get config reply
        //
        OFQueueGetConfigReply queueConfReply = factory.buildQueueGetConfigReply().build();
        updateAndTestCounter(queueConfReply,OFType.QUEUE_GET_CONFIG_REPLY.toString());

        // Queue get config request
        //
        OFQueueGetConfigRequest queueConfReq = factory.queueGetConfigRequest(OFPort.ZERO);
        updateAndTestCounter(queueConfReq,OFType.QUEUE_GET_CONFIG_REQUEST.toString());

        // Role Reply
        //
        OFRoleReply roleReply = factory.buildRoleReply().setRole(OFControllerRole.ROLE_EQUAL).build();
        updateAndTestCounter(roleReply,OFType.ROLE_REPLY.toString());

        //  Role Req
        //
        OFRoleRequest roleReq = factory.buildRoleRequest().setRole(OFControllerRole.ROLE_EQUAL).build();
        updateAndTestCounter(roleReq,OFType.ROLE_REQUEST.toString());

        // Set Async
        //
        OFAsyncSet setAsync = factory.buildAsyncSet().build();
        updateAndTestCounter(setAsync,OFType.SET_ASYNC.toString());

        // Set Config
        //
        OFSetConfig setConfig = factory.buildSetConfig().build();
        updateAndTestCounter(setConfig,OFType.SET_CONFIG.toString());

        // Stats Reply
        //
        OFStatsReply statsReply = factory.buildAggregateStatsReply().build();
        updateAndTestCounter(statsReply,OFType.STATS_REPLY.toString());

        // Stats Request
        //
        OFStatsRequest<?> statsRequest = factory.buildAggregateStatsRequest().build();
        updateAndTestCounter(statsRequest,OFType.STATS_REQUEST.toString());

        // Table Mod
        //
        OFTableMod tableMod = factory.buildTableMod().build();
        updateAndTestCounter(tableMod,OFType.TABLE_MOD.toString());
        
        factory = OFFactories.getFactory(OFVersion.OF_14);
        
        // Request Forward
        //
        OFRequestforward requestForward = factory.buildRequestforward().build();
        updateAndTestCounter(requestForward,OFType.REQUESTFORWARD.toString());
        
        // Bundle Add
        //
        OFBundleAddMsg bundleAdd = factory.buildBundleAddMsg()
        		.setBundleId(BundleId.NONE)
        		.setData(factory.buildPacketIn().setReason(OFPacketInReason.NO_MATCH).build())
        		.build();
        updateAndTestCounter(bundleAdd,OFType.BUNDLE_ADD_MESSAGE.toString());
        
        // Bundle Control
        //
        OFBundleCtrlMsg bundleControl = factory.buildBundleCtrlMsg()
        		.setBundleId(BundleId.NONE)
        		.setBundleCtrlType(OFBundleCtrlType.OPEN_REQUEST)
        		.build();
        updateAndTestCounter(bundleControl,OFType.BUNDLE_CONTROL.toString());
        
        // Role Status
        //
        OFRoleStatus roleStatus = factory.buildRoleStatus()
        		.setReason(OFControllerRoleReason.CONFIG)
        		.build();
        updateAndTestCounter(roleStatus,OFType.ROLE_STATUS.toString());
        
        // Table Status
        //
        OFTableStatus tableStatus = factory.buildTableStatus()
        		.setReason(OFTableReason.VACANCY_DOWN)
        		.setTable(factory.buildTableDesc().setTableId(TableId.ZERO).build())
        		.build();
        updateAndTestCounter(tableStatus,OFType.TABLE_STATUS.toString());

    }
}
