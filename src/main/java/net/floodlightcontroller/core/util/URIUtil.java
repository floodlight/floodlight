package net.floodlightcontroller.core.util;

import java.net.URI;

public class URIUtil {

    public static URI createURI(String hostname, int port) {
        return URI.create("tcp://" + hostname + ":" + port);
    }
}
