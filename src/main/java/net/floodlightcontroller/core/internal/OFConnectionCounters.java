package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.python.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Counters for per-connection statistics for OpenFlow
 * messages.
 * @author Alok Shankar <alok@bigswitch.com>
 */
public class OFConnectionCounters {
    public static final String COUNTER_MODULE = OFSwitchManager.class.getSimpleName();
    private static String dpidAndConnIdString;
    private static IDebugCounterService debugCounterService;
    /**
     * Counters for open flow message types
     */
    // Write Counters
    //
    private final IDebugCounter ctrWriteHello;
    private final IDebugCounter ctrWriteError;
    private final IDebugCounter ctrWriteEchoRequest;
    private final IDebugCounter ctrWriteEchoReply;
    private final IDebugCounter ctrWriteExperimenter;
    private final IDebugCounter ctrWriteFeaturesRequest;
    private final IDebugCounter ctrWriteFeaturesReply;
    private final IDebugCounter ctrWriteGetConfigRequest;
    private final IDebugCounter ctrWriteGetConfigReply;
    private final IDebugCounter ctrWriteSetConfig;
    private final IDebugCounter ctrWritePacketIn;
    private final IDebugCounter ctrWriteFlowRemoved;
    private final IDebugCounter ctrWritePacketOut;
    private final IDebugCounter ctrWritePortStatus;
    private final IDebugCounter ctrWriteFlowMod;
    private final IDebugCounter ctrWritePortMod;
    private final IDebugCounter ctrWriteStatsRequest;
    private final IDebugCounter ctrWriteStatsReply;
    private final IDebugCounter ctrWriteBarrierRequest;
    private final IDebugCounter ctrWriteBarrierReply;
    private final IDebugCounter ctrWriteQueueGetConfigRequest;
    private final IDebugCounter ctrWriteQueueGetConfigReply;
    private final IDebugCounter ctrWriteGroupMod;
    private final IDebugCounter ctrWriteTableMod;
    private final IDebugCounter ctrWriteRoleRequest;
    private final IDebugCounter ctrWriteRoleReply;
    private final IDebugCounter ctrWriteGetAsyncRequest;
    private final IDebugCounter ctrWriteGetAsyncReply;
    private final IDebugCounter ctrWriteSetAsync;
    private final IDebugCounter ctrWriteMeterMod;
    private final IDebugCounter ctrWriteRoleStatus;
    private final IDebugCounter ctrWriteTableStatus;   
    private final IDebugCounter ctrWriteRequestForward;
    private final IDebugCounter ctrWriteBundleControl;
    private final IDebugCounter ctrWriteBundleAdd;
    private final IDebugCounter ctrWriteControllerStatus;

    // Read Counters
    //
    private final IDebugCounter ctrReadHello;
    private final IDebugCounter ctrReadError;
    private final IDebugCounter ctrReadEchoRequest;
    private final IDebugCounter ctrReadEchoReply;
    private final IDebugCounter ctrReadExperimenter;
    private final IDebugCounter ctrReadFeaturesRequest;
    private final IDebugCounter ctrReadFeaturesReply;
    private final IDebugCounter ctrReadGetConfigRequest;
    private final IDebugCounter ctrReadGetConfigReply;
    private final IDebugCounter ctrReadSetConfig;
    private final IDebugCounter ctrReadPacketIn;
    private final IDebugCounter ctrReadPacketOut;
    private final IDebugCounter ctrReadFlowRemoved;
    private final IDebugCounter ctrReadPortStatus;
    private final IDebugCounter ctrReadFlowMod;
    private final IDebugCounter ctrReadPortMod;
    private final IDebugCounter ctrReadStatsRequest;
    private final IDebugCounter ctrReadStatsReply;
    private final IDebugCounter ctrReadBarrierRequest;
    private final IDebugCounter ctrReadBarrierReply;
    private final IDebugCounter ctrReadGetAsyncReply;
    private final IDebugCounter ctrReadGetAsyncRequest;
    private final IDebugCounter ctrReadGroupMod;
    private final IDebugCounter ctrReadMeterMod;
    private final IDebugCounter ctrReadQueueGetConfigReply;
    private final IDebugCounter ctrReadQueueGetConfigRequest;
    private final IDebugCounter ctrReadRoleRequest;
    private final IDebugCounter ctrReadRoleReply;
    private final IDebugCounter ctrReadSetAsync;
    private final IDebugCounter ctrReadTableMod;
    private final IDebugCounter ctrReadRoleStatus;
    private final IDebugCounter ctrReadTableStatus;   
    private final IDebugCounter ctrReadBundleAdd;
    private final IDebugCounter ctrReadBundleControl;
    private final IDebugCounter ctrReadRequestForward;
    private final IDebugCounter ctrReadControllerStatus;

    private static final Logger logger =
            LoggerFactory.getLogger(OFConnectionCounters.class);

    /**
     * Utility function to create description string and do counter registration
     * @param countersService
     * @param stringId The string ID
     * @param messageType Type of open flow message
     * @return the registered DebugCounter
     */
    IDebugCounter registerCounterLocal(IDebugCounterService countersService,
                                       String hierarchy,
                                       String stringId,
                                       String messageType){
        String counterHierarchy = stringId + hierarchy + "/" + messageType;
        String counterDescription = "Number of " + messageType +
                                        " messages in this connection";

        return countersService.registerCounter(COUNTER_MODULE, counterHierarchy,
                                               counterDescription);
    }

    public OFConnectionCounters(IDebugCounterService counters,
                                DatapathId dpid,
                                OFAuxId auxId) {

        Preconditions.checkNotNull(counters, "Counters must not be null");
        Preconditions.checkNotNull(dpid, "dpid must not be null");
        Preconditions.checkNotNull(auxId, "auxid must not be null");

        String stringId = dpid.toString() +":" + auxId.toString();
        dpidAndConnIdString = stringId;
        String hierarchy = "/write";
        
        debugCounterService = counters;

        // every level of the hierarchical counter has to be registered
        // even if they are not used

        counters.registerCounter(COUNTER_MODULE, stringId ,
                                 "Counter for this connection");

        registerCounterLocal(counters,
                             hierarchy,
                             stringId,
                             "");

        ctrWriteHello =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.HELLO.toString());
        ctrWriteError =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ERROR.toString());
        ctrWriteEchoRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ECHO_REQUEST.toString());
        ctrWriteEchoReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ECHO_REPLY.toString());
        ctrWriteExperimenter =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.EXPERIMENTER.toString());
        ctrWriteFeaturesRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.FEATURES_REQUEST.toString());
        ctrWriteFeaturesReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.FEATURES_REPLY.toString());
        ctrWriteGetConfigRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GET_CONFIG_REQUEST.toString());
        ctrWriteGetConfigReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GET_CONFIG_REPLY.toString());
        ctrWriteSetConfig =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.SET_CONFIG.toString());
        ctrWritePacketIn =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.PACKET_IN.toString());
        ctrWritePacketOut =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.PACKET_OUT.toString());
        ctrWriteFlowRemoved =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.FLOW_REMOVED.toString());
        ctrWritePortStatus =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.PORT_STATUS.toString());
        ctrWriteFlowMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.FLOW_MOD.toString());
        ctrWritePortMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.PORT_MOD.toString());
        ctrWriteStatsRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.STATS_REQUEST.toString());
        ctrWriteStatsReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.STATS_REPLY.toString());
        ctrWriteBarrierRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.BARRIER_REQUEST.toString());
        ctrWriteBarrierReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.BARRIER_REPLY.toString());
        ctrWriteGetAsyncReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GET_ASYNC_REPLY.toString());
        ctrWriteGetAsyncRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GET_ASYNC_REQUEST.toString());
        ctrWriteGroupMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GROUP_MOD.toString());
        ctrWriteMeterMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.METER_MOD.toString());
        ctrWriteQueueGetConfigReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.QUEUE_GET_CONFIG_REPLY.toString());
        ctrWriteQueueGetConfigRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.QUEUE_GET_CONFIG_REQUEST.toString());
        ctrWriteRoleReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ROLE_REPLY.toString());
        ctrWriteRoleRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ROLE_REQUEST.toString());
        ctrWriteSetAsync =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.SET_ASYNC.toString());
        ctrWriteTableMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.TABLE_MOD.toString());
        
        ctrWriteBundleAdd =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.BUNDLE_ADD_MESSAGE.toString());
        
        ctrWriteBundleControl =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.BUNDLE_CONTROL.toString());
        
        ctrWriteRequestForward =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.REQUESTFORWARD.toString());
        
        ctrWriteRoleStatus =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ROLE_STATUS.toString());
        
        ctrWriteTableStatus =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.TABLE_STATUS.toString());
        
        ctrWriteControllerStatus =
        		registerCounterLocal(counters,
                hierarchy,
                stringId,
                OFType.CONTROLLER_STATUS.toString());

        // Register Read Counters
        //
        hierarchy = "/read";

        registerCounterLocal(counters,
                             hierarchy,
                             stringId,
                             "");
        ctrReadHello =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.HELLO.toString());
        ctrReadError =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ERROR.toString());
        ctrReadEchoRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ECHO_REQUEST.toString());
        ctrReadEchoReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ECHO_REPLY.toString());
        ctrReadExperimenter =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.EXPERIMENTER.toString());
        ctrReadFeaturesRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.FEATURES_REQUEST.toString());
        ctrReadFeaturesReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.FEATURES_REPLY.toString());
        ctrReadGetConfigRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GET_CONFIG_REQUEST.toString());
        ctrReadGetConfigReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GET_CONFIG_REPLY.toString());
        ctrReadSetConfig =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.SET_CONFIG.toString());
        ctrReadPacketIn =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.PACKET_IN.toString());
        ctrReadPacketOut =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.PACKET_OUT.toString());
        ctrReadFlowRemoved =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.FLOW_REMOVED.toString());
        ctrReadPortStatus =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.PORT_STATUS.toString());
        ctrReadFlowMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.FLOW_MOD.toString());
        ctrReadPortMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.PORT_MOD.toString());
        ctrReadStatsRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.STATS_REQUEST.toString());
        ctrReadStatsReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.STATS_REPLY.toString());
        ctrReadBarrierRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.BARRIER_REQUEST.toString());
        ctrReadBarrierReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.BARRIER_REPLY.toString());
        ctrReadGetAsyncReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GET_ASYNC_REPLY.toString());
        ctrReadGetAsyncRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GET_ASYNC_REQUEST.toString());
        ctrReadGroupMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.GROUP_MOD.toString());
        ctrReadMeterMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.METER_MOD.toString());
        ctrReadQueueGetConfigReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.QUEUE_GET_CONFIG_REPLY.toString());
        ctrReadQueueGetConfigRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.QUEUE_GET_CONFIG_REQUEST.toString());
        ctrReadRoleReply =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ROLE_REPLY.toString());
        ctrReadRoleRequest =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ROLE_REQUEST.toString());
        ctrReadSetAsync =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.SET_ASYNC.toString());
        ctrReadTableMod =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.TABLE_MOD.toString());
        
        ctrReadBundleAdd =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.BUNDLE_ADD_MESSAGE.toString());
        
        ctrReadBundleControl =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.BUNDLE_CONTROL.toString());
        
        ctrReadRequestForward =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.REQUESTFORWARD.toString());
        
        ctrReadRoleStatus =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.ROLE_STATUS.toString());
        
        ctrReadTableStatus =
                registerCounterLocal(counters,
                                     hierarchy,
                                     stringId,
                                     OFType.TABLE_STATUS.toString());
        
        ctrReadControllerStatus =
        		registerCounterLocal(counters,
                hierarchy,
                stringId,
                OFType.CONTROLLER_STATUS.toString());
    }
    
    /**
     * Remove all counters from the IDebugCounterService. Should be done
     * if the switch connection disconnects from the controller, in which case all
     * the counters will be invalid.
     * @return true if successful; false if counter hierarchy was not found
     */
    public boolean uninstallCounters() {
    	return debugCounterService.removeCounterHierarchy(COUNTER_MODULE, dpidAndConnIdString);
    }

   /**
    * Update Write Counters for Open flow messages
    * @param ofm openflow message
    */
   public void updateWriteStats(OFMessage ofm) {
         switch(ofm.getType()){
            case BARRIER_REPLY:
                ctrWriteBarrierReply.increment();
                break;

            case BARRIER_REQUEST:
                ctrWriteBarrierRequest.increment();
                break;

            case ECHO_REPLY:
                ctrWriteEchoReply.increment();
                break;

            case ECHO_REQUEST:
                ctrWriteEchoRequest.increment();
                break;

            case ERROR:
                ctrWriteError.increment();
                break;

            case EXPERIMENTER:
                ctrWriteExperimenter.increment();
                break;

            case FEATURES_REPLY:
                ctrWriteFeaturesReply.increment();
                break;

            case FEATURES_REQUEST:
                ctrWriteFeaturesRequest.increment();
                break;

            case FLOW_MOD:
                ctrWriteFlowMod.increment();
                break;

            case FLOW_REMOVED:
                ctrWriteFlowRemoved.increment();
                break;

            case GET_ASYNC_REPLY:
                ctrWriteGetAsyncReply.increment();
                break;

            case GET_ASYNC_REQUEST:
                ctrWriteGetAsyncRequest.increment();
                break;

            case GET_CONFIG_REPLY:
                ctrWriteGetConfigReply.increment();
                break;

            case GET_CONFIG_REQUEST:
                ctrWriteGetConfigRequest.increment();
                break;

            case GROUP_MOD:
                ctrWriteGroupMod.increment();
                break;

            case HELLO:
                ctrWriteHello.increment();
                break;

            case METER_MOD:
                ctrWriteMeterMod.increment();
                break;

            case PACKET_IN:
                ctrWritePacketIn.increment();
                break;

            case PACKET_OUT:
                ctrWritePacketOut.increment();
                break;

            case PORT_MOD:
                ctrWritePortMod.increment();
                break;

            case PORT_STATUS:
                ctrWritePortStatus.increment();
                break;

            case QUEUE_GET_CONFIG_REPLY:
                ctrWriteQueueGetConfigReply.increment();
                break;

            case QUEUE_GET_CONFIG_REQUEST:
                ctrWriteQueueGetConfigRequest.increment();
                break;

            case ROLE_REPLY:
                ctrWriteRoleReply.increment();
                break;

            case ROLE_REQUEST:
                ctrWriteRoleRequest.increment();
                break;

            case SET_ASYNC:
                ctrWriteSetAsync.increment();
                break;

            case SET_CONFIG:
                ctrWriteSetConfig.increment();
                break;

            case STATS_REPLY:
                ctrWriteStatsReply.increment();
                break;

            case STATS_REQUEST:
                ctrWriteStatsRequest.increment();
                break;

            case TABLE_MOD:
                ctrWriteTableMod.increment();
                break;
                
            case BUNDLE_ADD_MESSAGE:
                ctrWriteBundleAdd.increment();
                break;
                
            case BUNDLE_CONTROL:
                ctrWriteBundleControl.increment();
                break;
                
            case REQUESTFORWARD:
                ctrWriteRequestForward.increment();
                break;
                
            case ROLE_STATUS:
                ctrWriteRoleStatus.increment();
                break;
                
            case TABLE_STATUS:
                ctrWriteTableStatus.increment();
                break;
                
            case CONTROLLER_STATUS:
            	ctrWriteControllerStatus.increment();
    			break;   
    			
            default:
                logger.warn(ofm.getType().toString() +
                            ": Invalid OpenFlow Messaqe!");
                break;
		
         }
    }

   /**
    * Update Read openflow counters for this connection
    * @param ofm Open Flow Message
    */
   public void updateReadStats(OFMessage ofm){
       switch(ofm.getType()){
           case BARRIER_REPLY:
               ctrReadBarrierReply.increment();
               break;

           case BARRIER_REQUEST:
               ctrReadBarrierRequest.increment();
               break;

           case ECHO_REPLY:
               ctrReadEchoReply.increment();
               break;

           case ECHO_REQUEST:
               ctrReadEchoRequest.increment();
               break;

           case ERROR:
               ctrReadError.increment();
               break;

           case EXPERIMENTER:
               ctrReadExperimenter.increment();
               break;

           case FEATURES_REPLY:
               ctrReadFeaturesReply.increment();
               break;

           case FEATURES_REQUEST:
               ctrReadFeaturesRequest.increment();
               break;

           case FLOW_MOD:
               ctrReadFlowMod.increment();
               break;

           case FLOW_REMOVED:
               ctrReadFlowRemoved.increment();
               break;

           case GET_ASYNC_REPLY:
               ctrReadGetAsyncReply.increment();
               break;

           case GET_ASYNC_REQUEST:
               ctrReadGetAsyncRequest.increment();
               break;

           case GET_CONFIG_REPLY:
               ctrReadGetConfigReply.increment();
               break;

           case GET_CONFIG_REQUEST:
               ctrReadGetConfigRequest.increment();
               break;

           case GROUP_MOD:
               ctrReadGroupMod.increment();
               break;

           case HELLO:
               ctrReadHello.increment();
               break;

           case METER_MOD:
               ctrReadMeterMod.increment();
               break;

           case PACKET_IN:
               ctrReadPacketIn.increment();
               break;

           case PACKET_OUT:
               ctrReadPacketOut.increment();
               break;

           case PORT_MOD:
               ctrReadPortMod.increment();
               break;

           case PORT_STATUS:
               ctrReadPortStatus.increment();
               break;

           case QUEUE_GET_CONFIG_REPLY:
               ctrReadQueueGetConfigReply.increment();
               break;

           case QUEUE_GET_CONFIG_REQUEST:
               ctrReadQueueGetConfigRequest.increment();
               break;

           case ROLE_REPLY:
               ctrReadRoleReply.increment();
               break;

           case ROLE_REQUEST:
               ctrReadRoleRequest.increment();
               break;

           case SET_ASYNC:
               ctrReadSetAsync.increment();
               break;

           case SET_CONFIG:
               ctrReadSetConfig.increment();
               break;

           case STATS_REPLY:
               ctrReadStatsReply.increment();
               break;

           case STATS_REQUEST:
               ctrReadStatsRequest.increment();
               break;

           case TABLE_MOD:
               ctrReadTableMod.increment();
               break;
               
           case BUNDLE_ADD_MESSAGE:
               ctrReadBundleAdd.increment();
               break;
               
           case BUNDLE_CONTROL:
               ctrReadBundleControl.increment();
               break;
               
           case REQUESTFORWARD:
               ctrReadRequestForward.increment();
               break;
               
           case ROLE_STATUS:
               ctrReadRoleStatus.increment();
               break;
               
           case TABLE_STATUS:
               ctrReadTableStatus.increment();
               break;

           case CONTROLLER_STATUS:
           		ctrReadControllerStatus.increment();
           		break;   
               
           default:
               logger.warn(ofm.getType().toString() +
                           ": Invalid OpenFlow Messaqe!");
               break;
        }
   }
}
