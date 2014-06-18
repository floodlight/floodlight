package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFBsnGentableDescStatsEntry;
import org.projectfloodlight.openflow.types.GenTableId;

/** A GenTable was not found in the {@link GenTableMap}.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class GenTableNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public GenTableNotFoundException(String name, Iterable<OFBsnGentableDescStatsEntry> available) {
        super(getMessageForName(name, available));
    }

    private static String getMessageForName(String name,
            Iterable<OFBsnGentableDescStatsEntry> available) {
        return String.format("Table not found: %s (available tables: %s)", name, availableDescr(available));
    }

    public GenTableNotFoundException(GenTableId id, Iterable<OFBsnGentableDescStatsEntry> available) {
        super(getMessageForId(id, available));
    }

    private static String getMessageForId(GenTableId id,
            Iterable<OFBsnGentableDescStatsEntry> available) {
        return String.format("Table not found: %s (available tables: %s)", id, availableDescr(available));
    }

    private static String availableDescr(Iterable<OFBsnGentableDescStatsEntry> available) {
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for(OFBsnGentableDescStatsEntry e: available) {
            if(!first)
                b.append(", ");
            first = false;
            b.append(e.getName()).append("=").append(e.getTableId());
        }
        return b.toString();
    }


}
