package net.floodlightcontroller.core.web;


import net.floodlightcontroller.core.web.serializers.StatsReplySerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=StatsReplySerializer.class)
public class StatsReply {
    private String datapath;
    private Object values;
    private String statType;

    public StatsReply() {}

    public StatsReply(String dpid,Object values,String type){
        this.datapath = dpid;
        this.values = values;
        this.statType = type;
    }
    public void setDatapathId(String dpid){
        this.datapath = dpid;
    }
    public void setValues(Object values){
        this.values = values;
    }
    public void setStatType(String type){
        this.statType = type;
    }
    public String getDatapathId(){
        return datapath;
    }
    public Object getValues(){
        return values;
    }
    public String getStatType(){
        return statType;
    }
    
}


