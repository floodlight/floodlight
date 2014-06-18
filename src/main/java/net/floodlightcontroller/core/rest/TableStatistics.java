package net.floodlightcontroller.core.rest;

import org.projectfloodlight.openflow.protocol.OFTableStatsEntry;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

public class TableStatistics {

    private final TableId tableId;
    private final long activeCount;
    private final U64 lookupCount;
    private final U64 matchedCount;

    private TableStatistics(TableId tableId, long activeCount, U64 lookupCount, U64 matchedCount) {
        this.tableId = tableId;
        this.activeCount = activeCount;
        this.lookupCount = lookupCount;
        this.matchedCount = matchedCount;
    }

    public static TableStatistics of(TableId tableId, long activeCount, U64 lookupCount, U64 matchedCount) {
        return new TableStatistics(tableId, activeCount, lookupCount, matchedCount);
    }

    public static TableStatistics of(OFTableStatsEntry entry) {
        return new TableStatistics(entry.getTableId(),
                entry.getActiveCount(), entry.getLookupCount(), entry.getMatchedCount());
    }

    public TableId getTableId() {
        return tableId;
    }

    public long getActiveCount() {
        return activeCount;
    }

    public U64 getLookupCount() {
        return lookupCount;
    }

    public U64 getMatchedCount() {
        return matchedCount;
    }
}