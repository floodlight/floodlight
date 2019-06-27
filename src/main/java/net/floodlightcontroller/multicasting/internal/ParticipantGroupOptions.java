package net.floodlightcontroller.multicasting.internal;

import org.projectfloodlight.openflow.types.TableId;

/**
 * @author Souvik Das (souvikdas95@yahoo.co.in)
 * 
 * Keeps flowmod and other options for a specific
 * participant group address
 *
 */
public class ParticipantGroupOptions {
    private final ParticipantGroupAddress groupAddress;
    
    private Integer flowPriority;
    private TableId tableId;
    private Integer queueId;
    private Integer idleTimeout;
    private Integer hardTimeout;
    
    public ParticipantGroupOptions(ParticipantGroupAddress groupAddress) {
        this.groupAddress = groupAddress;
    }
    
    public ParticipantGroupAddress getGroupAddress() {
        return groupAddress;
    }
    
    public Integer getFlowPriority() {
        return flowPriority;
    }
    
    public TableId getTableId() {
        return tableId;
    }
    
    public Integer getQueueId() {
        return queueId;
    }
    
    public Integer getIdleTimeout() {
        return idleTimeout;
    }
    
    public Integer getHardTimeout() {
        return hardTimeout;
    }
    
    public void setFlowPriority(Integer flowPriority) {
        this.flowPriority = flowPriority;
    }

    public void setTableId(TableId tableId) {
        this.tableId = tableId;
    }

    public void setQueueId(Integer queueId) {
        this.queueId = queueId;
    }

    public void setIdleTimeout(Integer idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public void setHardTimeout(Integer hardTimeout) {
        this.hardTimeout = hardTimeout;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        
        ParticipantGroupOptions that = (ParticipantGroupOptions) o;
        
        if (groupAddress == null || that.groupAddress == null || 
                !groupAddress.equals(that.groupAddress)) {
            return false;
        }
        
        if ((flowPriority == null && that.flowPriority != null) || 
            (flowPriority != null && !flowPriority.equals(that.flowPriority))) {
            return false;
        }
        
        if ((tableId == null && that.tableId != null) || 
            (tableId != null && !tableId.equals(that.tableId))) {
            return false;
        }
        
        if ((queueId == null && that.queueId != null) || 
            (queueId != null && !queueId.equals(that.queueId))) {
            return false;
        }
        
        if ((idleTimeout == null && that.idleTimeout != null) || 
            (idleTimeout != null && !idleTimeout.equals(that.idleTimeout))) {
            return false;
        }
        
        if ((hardTimeout == null && that.hardTimeout != null) || 
            (hardTimeout != null && !hardTimeout.equals(that.hardTimeout))) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public int hashCode() {
        int result = 0;
        
        if (groupAddress != null) {
            result = 31 * result + groupAddress.hashCode();
        }
        
        if (flowPriority != null) {
            result = 31 * result + flowPriority.hashCode();
        }
        
        if (tableId != null) {
            result = 31 * result + tableId.hashCode();
        }
        
        if (queueId != null) {
            result = 31 * result + queueId.hashCode();
        }
        
        if (idleTimeout != null) {
            result = 31 * result + idleTimeout.hashCode();
        }
        
        if (hardTimeout != null) {
            result = 31 * result + hardTimeout.hashCode();
        }
        
        return result;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        if (groupAddress != null) {
            sb.append("GroupAddress: {" + groupAddress + "}, ");
        }
        
        if (flowPriority != null) {
            sb.append("FlowPriority: {" + flowPriority + "}, ");
        }
        
        if (tableId != null) {
            sb.append("TableId: {" + tableId + "}, ");
        }
        
        if (queueId != null) {
            sb.append("QueueId: {" + queueId + "}, ");
        }
        
        if (idleTimeout != null) {
            sb.append("IdleTimeout: {" + idleTimeout + "}, ");
        }
        
        if (hardTimeout != null) {
            sb.append("HardTimeout: {" + hardTimeout + "}, ");
        }
        
        return sb.toString();
    }
}
