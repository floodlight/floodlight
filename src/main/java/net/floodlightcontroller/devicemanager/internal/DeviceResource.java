package net.floodlightcontroller.devicemanager.internal;

import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.devicemanager.Device;
import net.floodlightcontroller.devicemanager.IDeviceManagerService;

import org.openflow.util.HexString;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviceResource extends ServerResource {
    protected static Logger log = 
            LoggerFactory.getLogger(DeviceResource.class);
    
    @Get("json")
    public Map<String, Device> retrieve() {
        Map<String, Device> retMap = new HashMap<String, Device>();
        IDeviceManagerService dm = 
            (IDeviceManagerService)getContext().getAttributes().
                get(IDeviceManagerService.class.getCanonicalName());
        
        String param = (String) getRequestAttributes().get("device");
        
        if (param.toLowerCase().equals("all")) {
            // Get all devices
            for (Device d : dm.getDevices()) {
                retMap.put(HexString.toHexString(d.getDataLayerAddress()), d);
            }
        } else {
            // Get device by MAC
            Device dev = null;
            try {
                byte[] devMac = HexString.fromHexString(param);
                dev = dm.getDeviceByDataLayerAddress(devMac);
                if (dev != null) {
                    retMap.put(HexString.toHexString(dev.getDataLayerAddress()), dev);
                }
            } catch (NumberFormatException e) { } // ignore, return empty map
        }
        
        return retMap;
    }
}
