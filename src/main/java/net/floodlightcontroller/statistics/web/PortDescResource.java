package net.floodlightcontroller.statistics.web;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.PortDesc;

public class PortDescResource extends ServerResource{
	private static final Logger log = LoggerFactory.getLogger(PortDescResource.class);

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
				return Collections.singletonMap("ERROR", "Could not parse DPID " + d);
			}
		} /* else assume it's all */

		OFPort port = OFPort.ALL;
		if (!p.trim().equalsIgnoreCase("all")) {
			try {
				port = OFPort.of(Integer.parseInt(p));
			} catch (Exception e) {
				log.error("Could not parse port {}", p);
				return Collections.singletonMap("ERROR", "Could not parse port " + p);
			}
		}

		Set<PortDesc> pds;
		if (dpid.equals(DatapathId.NONE)) { /* do all DPIDs */
			if (port.equals(OFPort.ALL)) { /* do all ports --> do all DPIDs; do all ports */
				pds = new HashSet<PortDesc>(statisticsService.getPortDesc().values());
			} else {
				pds = new HashSet<PortDesc>();
				for (DatapathId id : switchService.getAllSwitchDpids()) { /* do all DPIDs; do specific port */
					PortDesc portDesc = statisticsService.getPortDesc(id, port);
					if (portDesc != null) {
						pds.add(portDesc);
					}
				}
			}
		} else { /* do specific DPID */
			if (!port.equals(OFPort.ALL)) { /* do specific port --> do specific DPID; do specific port */
				pds = new HashSet<PortDesc>(Collections.singleton(statisticsService.getPortDesc(dpid, port)));
			} else {
				pds = new HashSet<PortDesc>();
				//fix concurrency scenario
				IOFSwitch sw = switchService.getSwitch(dpid);
				if (sw == null){
					return Collections.singletonMap("ERROR", "Switch was not online: " + dpid);
				}
				for (OFPortDesc pd : sw.getPorts()) { /* do specific DPID; do all ports */
					PortDesc portDesc = statisticsService.getPortDesc(dpid, pd.getPortNo());
					if (portDesc != null) {
						pds.add(portDesc);
					}
				}
			}
		}
		return pds;
	}

}