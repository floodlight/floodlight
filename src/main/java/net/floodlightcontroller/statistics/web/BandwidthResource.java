package net.floodlightcontroller.statistics.web;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;

import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BandwidthResource extends ServerResource {
	private static final Logger log = LoggerFactory.getLogger(BandwidthResource.class);

	@Get("json")
	public Object retrieve() {
		IStatisticsService statisticsService = (IStatisticsService) getContext().getAttributes().get(IStatisticsService.class.getCanonicalName());
		IOFSwitchService switchService = (IOFSwitchService) getContext().getAttributes().get(IOFSwitchService.class.getCanonicalName());

		String d = (String) getRequestAttributes().get(SwitchStatisticsWebRoutable.DPID_STR);
		String p = (String) getRequestAttributes().get(SwitchStatisticsWebRoutable.PORT_STR);

		DatapathId dpid = DatapathId.NONE;

		if (!d.trim().equalsIgnoreCase("all")) {
			try {
				dpid = DatapathId.of(d);
			} catch (Exception e) {
				log.error("Could not parse DPID {}", d);
				return Collections.singletonMap("ERROR", "Could not parse DPID" + d);
			}
		} /* else assume it's all */

		OFPort port = OFPort.ALL;
		if (!p.trim().equalsIgnoreCase("all")) {
			try {
				port = OFPort.of(Integer.parseInt(p));
			} catch (Exception e) {
				log.error("Could not parse port {}", p);
				return Collections.singletonMap("ERROR", "Could not parse port" + p);
			}
		}

		Set<SwitchPortBandwidth> spbs;
		if (dpid.equals(DatapathId.NONE)) { /* do all DPIDs */
			if (port.equals(OFPort.ALL)) { /* do all ports */
				spbs = new HashSet<SwitchPortBandwidth>(statisticsService.getBandwidthConsumption().values());
			} else {
				spbs = new HashSet<SwitchPortBandwidth>();
				for (DatapathId id : switchService.getAllSwitchDpids()) {
					SwitchPortBandwidth spb = statisticsService.getBandwidthConsumption(id, port);
					if (spb != null) {
						spbs.add(spb);
					}
				}
			}
		} else {
			spbs = new HashSet<SwitchPortBandwidth>();
			for (OFPortDesc pd : switchService.getSwitch(dpid).getPorts()) {
				SwitchPortBandwidth spb = statisticsService.getBandwidthConsumption(dpid, pd.getPortNo());
				if (spb != null) {
					spbs.add(spb);
				}
			}
		}
		return spbs;
	}
}