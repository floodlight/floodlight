package net.floodlightcontroller.util;

import static org.junit.Assert.*;
import java.io.IOException;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;

import org.jboss.netty.channel.Channel;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFStatistics;


/**
 * A mock implementation of IFOSwitch we use for {@link OFMessageDamper}
 * 
 * We need to mock equals() and hashCode() but alas, EasyMock doesn't support
 * this. Sigh. And of course this happens to be the interface with the most
 * methods. 
 * @author gregor
 *
 */
public class OFMessageDamperMockSwitch implements IOFSwitch {
    OFMessage writtenMessage;
    FloodlightContext writtenContext;
    
    public OFMessageDamperMockSwitch() {
        reset();
    }
    
    /* reset this mock. I.e., clear the stored message previously written */
    public void reset() {
        writtenMessage = null;
        writtenContext = null;
    }
    
    /* assert that a message was written to this switch and that the 
     * written message and context matches the expected values 
     * @param expected
     * @param expectedContext
     */
    public void assertMessageWasWritten(OFMessage expected, 
                                        FloodlightContext expectedContext) {
        assertNotNull("No OFMessage was written", writtenMessage);
        assertEquals(expected, writtenMessage);
        assertEquals(expectedContext, writtenContext);
    }
    
    /*
     * assert that no message was written 
     */
    public void assertNoMessageWritten() {
        assertNull("OFMessage was written but didn't expect one", 
                      writtenMessage);
        assertNull("There was a context but didn't expect one", 
                      writtenContext);
    }
    
    /*
     * use hashCode() and equals() from Object
     */
    
    
    //-------------------------------------------------------
    // IOFSwitch: mocked methods
    @Override
    public void write(OFMessage m, FloodlightContext bc) throws IOException {
        assertNull("write() called but already have message", writtenMessage);
        assertNull("write() called but already have context", writtenContext);
        writtenContext = bc;
        writtenMessage = m;
    }
    
    //-------------------------------------------------------
    // IOFSwitch: not-implemented methods
    @Override
    public void write(List<OFMessage> msglist, FloodlightContext bc) 
            throws IOException {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public void disconnectOutputStream() {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public Channel getChannel() {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public void setFeaturesReply(OFFeaturesReply featuresReply) {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public void setSwitchProperties(OFDescriptionStatistics description) {
        assertTrue("Unexpected method call", false);
        // TODO Auto-generated method stub
    }
    
    @Override
    public Collection<OFPhysicalPort> getEnabledPorts() {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public Collection<Short> getEnabledPortNumbers() {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public OFPhysicalPort getPort(short portNumber) {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public OFPhysicalPort getPort(String portName) {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public void setPort(OFPhysicalPort port) {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public void deletePort(short portNumber) {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public void deletePort(String portName) {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public Collection<OFPhysicalPort> getPorts() {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public boolean portEnabled(short portName) {
        assertTrue("Unexpected method call", false);
        return false;
    }
    
    @Override
    public boolean portEnabled(String portName) {
        assertTrue("Unexpected method call", false);
        return false;
    }
    
    @Override
    public boolean portEnabled(OFPhysicalPort port) {
        assertTrue("Unexpected method call", false);
        return false;
    }
    
    @Override
    public long getId() {
        assertTrue("Unexpected method call", false);
        return 0;
    }
    
    @Override
    public String getStringId() {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public Map<Object, Object> getAttributes() {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public Date getConnectedSince() {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public int getNextTransactionId() {
        assertTrue("Unexpected method call", false);
        return 0;
    }
    
    @Override
    public Future<List<OFStatistics>>
            getStatistics(OFStatisticsRequest request) throws IOException {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public boolean isConnected() {
        assertTrue("Unexpected method call", false);
        return false;
    }
    
    @Override
    public void setConnected(boolean connected) {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public Role getRole() {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public boolean isActive() {
        assertTrue("Unexpected method call", false);
        return false;
    }
    
    @Override
    public void deliverStatisticsReply(OFMessage reply) {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public void cancelStatisticsReply(int transactionId) {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public void cancelAllStatisticsReplies() {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public boolean hasAttribute(String name) {
        assertTrue("Unexpected method call", false);
        return false;
    }
    
    @Override
    public Object getAttribute(String name) {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public void setAttribute(String name, Object value) {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public Object removeAttribute(String name) {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public void clearAllFlowMods() {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public boolean updateBroadcastCache(Long entry, Short port) {
        assertTrue("Unexpected method call", false);
        return false;
    }
    
    @Override
    public Map<Short, Long> getPortBroadcastHits() {
        assertTrue("Unexpected method call", false);
        return null;
    }
    
    @Override
    public void sendStatsQuery(OFStatisticsRequest request, int xid,
                               IOFMessageListener caller)
                                                         throws IOException {
        assertTrue("Unexpected method call", false);
    }
    
    @Override
    public void flush() {
        assertTrue("Unexpected method call", false);
    }

    @Override
    public Future<OFFeaturesReply> getFeaturesReplyFromSwitch()
            throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void deliverOFFeaturesReply(OFMessage reply) {
        // TODO Auto-generated method stub

    }

    @Override
    public void cancelFeaturesReply(int transactionId) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getBuffers() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getActions() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getCapabilities() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public byte getTables() {
        // TODO Auto-generated method stub
        return 0;
    }

}