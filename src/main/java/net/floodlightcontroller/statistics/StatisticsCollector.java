package net.floodlightcontroller.statistics;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFPortStatsEntry;
import org.projectfloodlight.openflow.protocol.OFPortStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterSerializerVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.statistics.web.SwitchStatisticsWebRoutable;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.NodePortTuple;

public class StatisticsCollector implements IFloodlightModule, IStatisticsService {
	private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);

	private static IOFSwitchService switchService;
	private static IThreadPoolService threadPoolService;
	private static IRestApiService restApiService;

	private static int portStatsInterval = 10;

	private static final long BITS_PER_BYTE = 8;
	private static final long MILLIS_PER_SEC = 1000;

	private static final HashMap<NodePortTuple, SwitchPortBandwidth> portStats = new HashMap<NodePortTuple, SwitchPortBandwidth>();
	private static final HashMap<NodePortTuple, SwitchPortBandwidth> tentativePortStats = new HashMap<NodePortTuple, SwitchPortBandwidth>();

	private class PortStatsCollector implements Runnable {

		@Override
		public void run() {
			Map<DatapathId, List<OFStatsReply>> replies = getSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.PORT);
			for (Entry<DatapathId, List<OFStatsReply>> e : replies.entrySet()) {
				for (OFStatsReply r : e.getValue()) {
					OFPortStatsReply psr = (OFPortStatsReply) r;
					for (OFPortStatsEntry pse : psr.getEntries()) {
						NodePortTuple npt = new NodePortTuple(e.getKey(), pse.getPortNo());
						SwitchPortBandwidth spb;
						if (portStats.containsKey(npt) || tentativePortStats.containsKey(npt)) {
							if (portStats.containsKey(npt)) { /* update */
								spb = portStats.get(npt);
							} else if (tentativePortStats.containsKey(npt)) { /* finish */
								spb = tentativePortStats.get(npt);
								tentativePortStats.remove(npt);
							} else {
								log.error("Inconsistent state between tentative and official port stats lists.");
								return;
							}

							/* Get counted bytes over the elapsed period. Check for counter overflow. */
							U64 rxBytesCounted;
							U64 txBytesCounted;
							if (spb.getPriorByteValueRx().compareTo(pse.getRxBytes()) > 0) { /* overflow */
								U64 upper = U64.NO_MASK.subtract(spb.getPriorByteValueRx());
								U64 lower = pse.getRxBytes();
								rxBytesCounted = upper.add(lower);
							} else {
								rxBytesCounted = pse.getRxBytes().subtract(spb.getPriorByteValueRx());
							}
							if (spb.getPriorByteValueTx().compareTo(pse.getTxBytes()) > 0) { /* overflow */
								U64 upper = U64.NO_MASK.subtract(spb.getPriorByteValueTx());
								U64 lower = pse.getTxBytes();
								txBytesCounted = upper.add(lower);
							} else {
								txBytesCounted = pse.getTxBytes().subtract(spb.getPriorByteValueTx());
							}
							long timeDifSec = (System.currentTimeMillis() - spb.getUpdateTime()) / MILLIS_PER_SEC;
							portStats.put(npt, SwitchPortBandwidth.of(npt.getNodeId(), npt.getPortId(), 
									U64.ofRaw((rxBytesCounted.getValue() * BITS_PER_BYTE) / timeDifSec), 
									U64.ofRaw((txBytesCounted.getValue() * BITS_PER_BYTE) / timeDifSec), 
									pse.getRxBytes(), pse.getTxBytes())
									);
							
						} else { /* initialize */
							tentativePortStats.put(npt, SwitchPortBandwidth.of(npt.getNodeId(), npt.getPortId(), U64.ZERO, U64.ZERO, pse.getRxBytes(), pse.getTxBytes()));
						}
					}
				}
			}
		}
	}

	private class GetStatisticsThread extends Thread {
		private List<OFStatsReply> statsReply;
		private DatapathId switchId;
		private OFStatsType statType;

		public GetStatisticsThread(DatapathId switchId, OFStatsType statType) {
			this.switchId = switchId;
			this.statType = statType;
			this.statsReply = null;
		}

		public List<OFStatsReply> getStatisticsReply() {
			return statsReply;
		}

		public DatapathId getSwitchId() {
			return switchId;
		}

		@Override
		public void run() {
			statsReply = getSwitchStatistics(switchId, statType);
		}
	}
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IStatisticsService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IStatisticsService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IOFSwitchService.class);
		l.add(IThreadPoolService.class);
		l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		switchService = context.getServiceImpl(IOFSwitchService.class);
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);

		//TODO Map<String, String> config = context.getConfigParams(this);
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new PortStatsCollector(), portStatsInterval, portStatsInterval, TimeUnit.SECONDS);
		restApiService.addRestletRoutable(new SwitchStatisticsWebRoutable());
	}

	@Override
	public SwitchPortBandwidth getBandwidthConsumption(DatapathId dpid, OFPort p) {
		return portStats.get(new NodePortTuple(dpid, p));
	}
	

	@Override
	public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthConsumption() {
		return Collections.unmodifiableMap(portStats);
	}

	private Map<DatapathId, List<OFStatsReply>> getSwitchStatistics(Set<DatapathId> dpids, OFStatsType statsType) {
		HashMap<DatapathId, List<OFStatsReply>> model = new HashMap<DatapathId, List<OFStatsReply>>();

		List<GetStatisticsThread> activeThreads = new ArrayList<GetStatisticsThread>(dpids.size());
		List<GetStatisticsThread> pendingRemovalThreads = new ArrayList<GetStatisticsThread>();
		GetStatisticsThread t;
		for (DatapathId d : dpids) {
			t = new GetStatisticsThread(d, statsType);
			activeThreads.add(t);
			t.start();
		}

		// Join all the threads after the timeout. Set a hard timeout
		// of 12 seconds for the threads to finish. If the thread has not
		// finished the switch has not replied yet and therefore we won't
		// add the switch's stats to the reply.
		for (int iSleepCycles = 0; iSleepCycles < portStatsInterval; iSleepCycles++) {
			for (GetStatisticsThread curThread : activeThreads) {
				if (curThread.getState() == State.TERMINATED) {
					model.put(curThread.getSwitchId(), curThread.getStatisticsReply());
					pendingRemovalThreads.add(curThread);
				}
			}

			// remove the threads that have completed the queries to the switches
			for (GetStatisticsThread curThread : pendingRemovalThreads) {
				activeThreads.remove(curThread);
			}
			// clear the list so we don't try to double remove them
			pendingRemovalThreads.clear();

			// if we are done finish early so we don't always get the worst case
			if (activeThreads.isEmpty()) {
				break;
			}

			// sleep for 1 s here
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				log.error("Interrupted while waiting for statistics", e);
			}
		}

		return model;
	}

	@SuppressWarnings("unchecked")
	protected List<OFStatsReply> getSwitchStatistics(DatapathId switchId, OFStatsType statsType) {
		IOFSwitch sw = switchService.getSwitch(switchId);
		ListenableFuture<?> future;
		List<OFStatsReply> values = null;
		Match match;
		if (sw != null) {
			OFStatsRequest<?> req = null;
			switch (statsType) {
			case FLOW:
				match = sw.getOFFactory().buildMatch().build();
				req = sw.getOFFactory().buildFlowStatsRequest()
						.setMatch(match)
						.setOutPort(OFPort.ANY)
						.setTableId(TableId.ALL)
						.build();
				break;
			case AGGREGATE:
				match = sw.getOFFactory().buildMatch().build();
				req = sw.getOFFactory().buildAggregateStatsRequest()
						.setMatch(match)
						.setOutPort(OFPort.ANY)
						.setTableId(TableId.ALL)
						.build();
				break;
			case PORT:
				req = sw.getOFFactory().buildPortStatsRequest()
				.setPortNo(OFPort.ANY)
				.build();
				break;
			case QUEUE:
				req = sw.getOFFactory().buildQueueStatsRequest()
				.setPortNo(OFPort.ANY)
				.setQueueId(UnsignedLong.MAX_VALUE.longValue())
				.build();
				break;
			case DESC:
				// pass - nothing todo besides set the type above
				req = sw.getOFFactory().buildDescStatsRequest()
				.build();
				break;
			case GROUP:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupStatsRequest()				
							.build();
				}
				break;

			case METER:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterStatsRequest()
							.setMeterId(OFMeterSerializerVer13.ALL_VAL)
							.build();
				}
				break;

			case GROUP_DESC:			
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupDescStatsRequest()			
							.build();
				}
				break;

			case GROUP_FEATURES:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupFeaturesStatsRequest()
							.build();
				}
				break;

			case METER_CONFIG:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterConfigStatsRequest()
							.build();
				}
				break;

			case METER_FEATURES:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterFeaturesStatsRequest()
							.build();
				}
				break;

			case TABLE:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildTableStatsRequest()
							.build();
				}
				break;

			case TABLE_FEATURES:	
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildTableFeaturesStatsRequest()
							.build();		
				}
				break;
			case PORT_DESC:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildPortDescStatsRequest()
							.build();
				}
				break;
			case EXPERIMENTER:		
			default:
				log.error("Stats Request Type {} not implemented yet", statsType.name());
				break;
			}

			try {
				if (req != null) {
					future = sw.writeStatsRequest(req); 
					values = (List<OFStatsReply>) future.get(portStatsInterval / 2, TimeUnit.SECONDS);
				}
			} catch (Exception e) {
				log.error("Failure retrieving statistics from switch {}. {}", sw, e);
			}
		}
		return values;
	}
}