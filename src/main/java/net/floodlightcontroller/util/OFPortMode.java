package net.floodlightcontroller.util;

/**
 * Define the different operating modes of a switch
 * port as trunk, access, and both (trunk and access).
 * 
 * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
 */

public enum OFPortMode {
	TRUNK,
	ACCESS,
	TRUNK_AND_ACCESS
}
