package net.floodlightcontroller.routing;

import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;

import java.util.ArrayList;

/**
 * @author Qing Wang at 12/20/17
 */
public class VirtualGateway {
    private String name;
    private MacAddress gatewayMac;
    private ArrayList<GatewayInterface> interfaces;

    public String getName() {
        return this.name;
    }

    public MacAddress getGatewayMac() {
        return this.gatewayMac;
    }



    class GatewayInterface {
        private MacAddress mac;
        private IPv4Address ip;

        protected GatewayInterface(MacAddress mac, IPv4Address ip) {
            this.setMac(mac);
            this.setIp(ip);
        }

        public MacAddress getMac() {
            return this.mac;
        }

        public IPv4Address getIp() {
            return this.ip;
        }

        public void setMac(MacAddress mac) {
            this.mac = mac;
        }

        public void setIp(IPv4Address ip) {
            this.ip = ip;
        }

        @Override
        public String toString() {
            return "GatewayInterface{" +
                    "mac=" + mac +
                    ", ip=" + ip +
                    '}';
        }
    }



}
