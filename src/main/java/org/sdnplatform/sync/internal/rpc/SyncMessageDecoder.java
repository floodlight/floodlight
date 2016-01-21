package org.sdnplatform.sync.internal.rpc;

import net.floodlightcontroller.core.util.ThriftFrameDecoder;

import org.sdnplatform.sync.thrift.SyncMessage;

public class SyncMessageDecoder extends ThriftFrameDecoder<SyncMessage> {

    public SyncMessageDecoder(int maxSize) {
        super(maxSize);
    }

    @Override
    protected SyncMessage allocateMessage() {
        return new SyncMessage();
    }

}
