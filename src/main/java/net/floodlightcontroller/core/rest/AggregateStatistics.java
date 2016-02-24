package net.floodlightcontroller.core.rest;

import org.projectfloodlight.openflow.protocol.OFAggregateStatsReply;
import org.projectfloodlight.openflow.types.U64;

public class AggregateStatistics {

    private final U64 packetCount;
    private final U64 byteCount;
    private final long flowCount;

    private AggregateStatistics(U64 packetCount, U64 byteCount, long flowCount) {
        this.packetCount = packetCount;
        this.byteCount = byteCount;
        this.flowCount = flowCount;
    }

    public static AggregateStatistics of(U64 packetCount, U64 byteCount,
            long flowCount) {
        return new AggregateStatistics(packetCount, byteCount, flowCount);
    }

    public static AggregateStatistics of(OFAggregateStatsReply statsReply) {
        return new AggregateStatistics(statsReply.getPacketCount(),
                statsReply.getByteCount(), statsReply.getFlowCount());
    }

    public U64 getPacketCount() {
        return packetCount;
    }

    public U64 getByteCount() {
        return byteCount;
    }

    public long getFlowCount() {
        return flowCount;
    }
}
