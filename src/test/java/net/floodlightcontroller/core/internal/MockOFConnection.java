package net.floodlightcontroller.core.internal;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Date;
import net.floodlightcontroller.core.IOFConnectionBackend;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

public class MockOFConnection implements IOFConnectionBackend {

    private final DatapathId id;
    private final OFAuxId auxId;
    private Date connectedSince;
    private boolean connected;
    private SocketAddress localInetAddress, remoteInetAddress;
    private OFFactory factory;
    private final List<OFMessage> messages;
    private final Map<Long, RequestAndFuture<?>>requests;
    private IOFConnectionListener listener;

    public MockOFConnection(DatapathId id, OFAuxId auxId){
        this.id = id;
        this.auxId = auxId;

        this.setDefaultAddresses();
        this.messages = new ArrayList<>();
        this.requests = new HashMap<>();
    }

    private void setDefaultAddresses() {
        SocketAddress socketAddress = null;
        try {
            byte[] addressBytes = {1, 1, 1, (byte)(this.id.getLong()%255)};
            InetAddress inetAddress = InetAddress.getByAddress(addressBytes);
            socketAddress = new InetSocketAddress(inetAddress, 7847);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.remoteInetAddress = socketAddress;

        socketAddress = null;
        try {
            byte[] addressBytes = {127, 0, 0, 1};
            InetAddress inetAddress = InetAddress.getByAddress(addressBytes);
            socketAddress = new InetSocketAddress(inetAddress, 7847);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.localInetAddress = socketAddress;
    }

    @Override
    public void write(OFMessage m) {
        messages.add(m);
    }

    @Override
    public void write(Iterable<OFMessage> msglist) {
        Iterables.addAll(messages, msglist);
    }

    static class RequestAndFuture<R extends OFMessage> {
        final OFRequest<R> request;
        final SettableFuture<R> replyFuture;

        public RequestAndFuture(OFRequest<R> request) {
            this.request = request;
            this.replyFuture = SettableFuture.create();
        }

        public OFRequest<R> getRequest() {
            return request;
        }

        public SettableFuture<R> getReplyFuture() {
            return replyFuture;
        }

    }

    @Override
    public <R extends OFMessage> ListenableFuture<R>
            writeRequest(OFRequest<R> request) {
        RequestAndFuture<R> raf = new RequestAndFuture<>(request);
        messages.add(request);
        requests.put(request.getXid(), raf);
        return raf.getReplyFuture();
    }

    @Override
    public <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>>
            writeStatsRequest(OFStatsRequest<REPLY> request) {
        return null;
    }

    public void setConnectedSince(Date connectedSince) {
        this.connectedSince = connectedSince;
    }

    @Override
    public Date getConnectedSince() {
        return this.connectedSince;
    }

    @Override
    public void flush() {
        // no op
    }

    @Override
    public DatapathId getDatapathId() {
        return this.id;
    }

    @Override
    public OFAuxId getAuxId() {
        return this.auxId;
    }

    public void setRemoteInetAddress(SocketAddress address){
        this.remoteInetAddress = address;
    }

    @Override
    public SocketAddress getRemoteInetAddress() {
        return this.remoteInetAddress;
    }

    public void setLocalInetAddress(SocketAddress address){
        this.localInetAddress = address;
    }

    @Override
    public SocketAddress getLocalInetAddress() {
        return this.localInetAddress;
    }

    public void setOFFactory(OFFactory factory) {
        this.factory = factory;
    }

    @Override
    public OFFactory getOFFactory() {
        return this.factory;
    }

    @Override
    public void disconnect() {
        this.connected = false;
    }

    @Override
    public void cancelAllPendingRequests() {
       // no op
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public boolean isConnected() {
        return this.connected;
    }

    public void setConnected(boolean conection) {
       this.connected = true;
    }

    @Override
    public void setListener(IOFConnectionListener listener) {
        this.listener = listener;
    }

    // for interacting with the action
    public List<OFMessage> getMessages() {
        return messages;
    }

    public Map<Long, RequestAndFuture<?>> getRequests() {
        return requests;
    }

    public IOFConnectionListener getListener() {
        return listener;
    }

    public void clearMessages() {
        this.messages.clear();
        this.requests.clear();
    }

    public OFMessage retrieveMessage() {
        return this.messages.remove(0);
    }
}
