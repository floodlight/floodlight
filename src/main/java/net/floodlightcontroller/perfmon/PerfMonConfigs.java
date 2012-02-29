package net.floodlightcontroller.perfmon;

public class PerfMonConfigs {
    /***
     * procTimeMonitoringState: true if monitoring is on, default is false
     * this variable is controller using a cli under the controller node
     * (config-controller)> [no] performance-monitor processing-time
     */
    protected boolean procTimeMonitoringState;
    // overall per-component performance monitoring knob; off by default
    protected boolean procTimePerCompMonitoringState;
    // knob for database performance monitoring
    protected boolean dbTimePerfMonState;

    public boolean isProcTimeMonitoringState() {
        return procTimeMonitoringState;
    }
    public void setProcTimeMonitoringState(
                    boolean procTimeMonitoringState) {
        this.procTimeMonitoringState = procTimeMonitoringState;
    }
    public boolean isProcTimePerCompMonitoringState() {
        return procTimePerCompMonitoringState;
    }
    public void setProcTimePerCompMonitoringState(
            boolean procTimePerCompMonitoringState) {
        this.procTimePerCompMonitoringState = 
                    procTimePerCompMonitoringState;
    }
    public boolean isDbTimePerfMonState() {
        return dbTimePerfMonState;
    }
    public void setDbTimePerfMonState(boolean dbTimePerfMonState) {
        this.dbTimePerfMonState = dbTimePerfMonState;
    }

    public PerfMonConfigs() {
        procTimeMonitoringState        = false;
        procTimePerCompMonitoringState = false;
        dbTimePerfMonState             = false;
    }
}