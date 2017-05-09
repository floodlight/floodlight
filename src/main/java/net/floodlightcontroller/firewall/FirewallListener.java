# By Benamrane Fouad

package net.floodlightcontroller.firewall;

public interface FirewallListener {
public void updateNewRule(FirewallRule R);
public void updateRemoveRule(FirewallRule R);
public void firewallActivated();

}
