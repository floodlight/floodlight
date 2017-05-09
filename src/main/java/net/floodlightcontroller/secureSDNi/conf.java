# By Benamrane Fouad

package net.floodlightcontroller.secureSDNi;

import java.util.Arrays;
import java.util.List;

public class conf {

		public String Mode="Notification";//"Service","Full"
		public List<String> l = Arrays.asList("192.168.10.13","192.168.10.14","192.168.10.15","192.168.10.16");
		public int port=9999;
		public boolean SSL=false;
		public boolean firewall=false;
		public boolean LoadBalancer=false;
		
		public boolean isLoadBalancer() {
			return LoadBalancer;
		}

		public boolean isFirewall() {
			return firewall;
		}

		public boolean isSSL() {
			return SSL;
		}
		public void setSSL(boolean sSL) {
			SSL = sSL;
		}
		
		public String getMode() {
			return Mode;
		}
		public List<String> getL() {
			return l;
		}
		public int getPort() {
			return port;
		}
}