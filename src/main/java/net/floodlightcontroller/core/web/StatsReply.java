package net.floodlightcontroller.core.web;


import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.core.web.serializers.StatsReplySerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=StatsReplySerializer.class)
public class StatsReply {
    private DatapathId datapath;
    private Object values;
    private OFStatsType statType;

    public StatsReply() {}

    public StatsReply(DatapathId dpid, Object values, OFStatsType type){
        this.datapath = dpid;
        this.values = values;
        this.statType = type;
    }
    public void setDatapathId(DatapathId dpid){
        this.datapath = dpid;
    }
    public void setValues(Object values){
        this.values = values;
    }
    public void setStatType(OFStatsType type){
        this.statType = type;
    }
    public DatapathId getDatapathId(){
        return datapath;
    }
    public Object getValues(){
        return values;
    }
    public OFStatsType getStatType(){
        return statType;
    }
    
}


