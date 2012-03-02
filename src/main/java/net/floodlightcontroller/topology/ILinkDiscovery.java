package net.floodlightcontroller.topology;

public interface ILinkDiscovery {
    public enum LinkType {
        INVALID_LINK, DIRECT_LINK, MULTIHOP_LINK, TUNNEL
    };
}
