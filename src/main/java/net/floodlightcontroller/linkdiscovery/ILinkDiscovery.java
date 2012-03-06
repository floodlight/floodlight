package net.floodlightcontroller.linkdiscovery;

public interface ILinkDiscovery {
    public enum LinkType {
        INVALID_LINK, DIRECT_LINK, MULTIHOP_LINK, TUNNEL
    };
}
