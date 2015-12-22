package net.floodlightcontroller.core.internal;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Date;

import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.IOFMessageWriter;
import net.floodlightcontroller.core.SwitchDisconnectedException;
import net.floodlightcontroller.util.IterableUtils;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

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
    public boolean write(OFMessage m) {
        warn();
        return false;
    }

    @Override
    public Collection<OFMessage> write(Iterable<OFMessage> msgList) {
        warn();
        return IterableUtils.toCollection(msgList);
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
    public <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(
            OFStatsRequest<REPLY> request) {
        return Futures.immediateFailedFuture(new SwitchDisconnectedException(getDatapathId()));
    }

    @Override
    public void cancelAllPendingRequests() {
        // noop
    }

    @Override
    public <R extends OFMessage> ListenableFuture<R> writeRequest(OFRequest<R> request) {
        return Futures.immediateFailedFuture(new SwitchDisconnectedException(getDatapathId()));
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

	@Override
	public U64 getLatency() {
		return U64.ZERO;
	}

	@Override
	public void updateLatency(U64 latency) {
		// noop
	}
}