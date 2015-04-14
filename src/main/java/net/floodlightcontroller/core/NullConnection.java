package net.floodlightcontroller.core;

import java.net.SocketAddress;
import java.util.List;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import net.floodlightcontroller.core.internal.IOFConnectionListener;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NullConnection implements IOFConnectionBackend, IOFMessageWriter {
    private static final Logger logger = LoggerFactory.getLogger(NullConnection.class);

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public Date getConnectedSince() {
        return null;
    }

    private void warn() {
        logger.debug("Switch {} not connected -- cannot send message", getDatapathId());
    }

    @Override
    public void write(OFMessage m) {
        warn();
    }

    @Override
    public void write(Iterable<OFMessage> msglist) {
        warn();
    }

    @Override
    public SocketAddress getRemoteInetAddress() {
        return null;
    }

    @Override
    public SocketAddress getLocalInetAddress() {
        return null;
    }

    @Override
    public OFFactory getOFFactory() {
        return OFFactories.getFactory(OFVersion.OF_13);
    }

    @Override
    public <REPLY extends OFStatsReply> CompletableFuture<List<REPLY>> writeStatsRequest(
            OFStatsRequest<REPLY> request) {
        CompletableFuture<List<REPLY>> future = new CompletableFuture<>();
        future.completeExceptionally(new SwitchDisconnectedException(getDatapathId()));
        return future;
    }

    @Override
    public void cancelAllPendingRequests() {
        // noop
    }

    @Override
    public void flush() {
        // noop
    }

    @Override
    public <R extends OFMessage> CompletableFuture<R> writeRequest(OFRequest<R> request) {
        CompletableFuture<R> future = new CompletableFuture<>();
        future.completeExceptionally(new SwitchDisconnectedException(getDatapathId()));
        return future;
    }

    @Override
    public void disconnect(){
        // noop
    }

    public void disconnected() {
        // noop
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public DatapathId getDatapathId() {
        return DatapathId.NONE;
    }

    @Override
    public OFAuxId getAuxId() {
        return OFAuxId.MAIN;
    }

    @Override
    public void setListener(IOFConnectionListener listener) {
    }

}