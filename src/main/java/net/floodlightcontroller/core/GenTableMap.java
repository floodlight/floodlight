package net.floodlightcontroller.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.projectfloodlight.openflow.protocol.OFBsnGentableDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFBsnGentableDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFBsnGentableDescStatsRequest;
import org.projectfloodlight.openflow.types.GenTableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

/** A registry for GenTables. Initialized during switch handshake, based on a
 *  {@link OFBsnGentableDescStatsRequest}. The table for a particular switch
 *  can be retrieved via {@link IOFSwitch#getGenTableMap()}.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
@Immutable
public class GenTableMap {
    private static final Logger logger = LoggerFactory.getLogger(GenTableMap.class);

    private final Map<GenTableId, OFBsnGentableDescStatsEntry> idMap;
    private final Map<String, OFBsnGentableDescStatsEntry> nameMap;

    // an empty gentable map
    private GenTableMap() {
        idMap = ImmutableMap.of();
        nameMap = ImmutableMap.of();
    }

    public GenTableMap(Iterable<OFBsnGentableDescStatsEntry> entries) {
        // note: not using an ImmutableMap.Builder here, because we need to
        // check for duplicates
        Map<GenTableId, OFBsnGentableDescStatsEntry> idBuildMap = new LinkedHashMap<>();
        Map<String, OFBsnGentableDescStatsEntry> nameBuildMap = new LinkedHashMap<>();

        for (OFBsnGentableDescStatsEntry e : entries) {
            GenTableId id = e.getTableId();
            String name = e.getName();
            if (idBuildMap.containsKey(id)) {
                logger.warn("Duplicate table id " + id + " - entry {} present. Ignoring new entry {}",
                        idBuildMap.get(id), e);
                continue;
            }
            if (nameBuildMap.containsKey(name)) {
                logger.warn(
                        "Duplicate table name " + name + " - entry named {} present. Ignoring new entry {}",
                        nameBuildMap.get(name), e);
                continue;
            }
            idBuildMap.put(id, e);
            nameBuildMap.put(name, e);
        }
        this.idMap = ImmutableMap.copyOf(idBuildMap);
        this.nameMap = ImmutableMap.copyOf(nameBuildMap);
    }

    public Set<GenTableId> getIds() {
        return idMap.keySet();
    }

    public Set<String> getNames() {
        return nameMap.keySet();
    }

    public Collection<OFBsnGentableDescStatsEntry> getEntries() {
        return idMap.values();
    }

    public boolean hasEntry(GenTableId id) {
        return idMap.containsKey(id);
    }

    /** retrieve a GenTable Description from the map by id.
     *
     * @param id
     * @return the retrieved gen  table description.
     * @throws GenTableNotFoundException if no gentable with the given id was found in the map
     */
    @Nonnull
    public OFBsnGentableDescStatsEntry getEntry(GenTableId id) throws GenTableNotFoundException {
        OFBsnGentableDescStatsEntry entry = idMap.get(id);
        if(entry == null)
            throw new GenTableNotFoundException(id, idMap.values());
        return entry;
    }

    public boolean hasEntry(String name) {
        return nameMap.containsKey(name);
    }

    /** retrieve a GenTable Description from the map by name.
     *
     * @param name
     * @return the retrieved gen table description.
     * @throws GenTableNotFoundException if no gentable with the given name was found in the map
     */
    @Nonnull
    public OFBsnGentableDescStatsEntry getEntry(String name) throws GenTableNotFoundException {
        OFBsnGentableDescStatsEntry entry = nameMap.get(name);
        if(entry == null)
            throw new GenTableNotFoundException(name, nameMap.values());
        return entry;
    }

    public final static GenTableMap empty() {
        return new GenTableMap();
    }

    public static GenTableMap of(Iterable<OFBsnGentableDescStatsReply> replies) {
        List<OFBsnGentableDescStatsEntry> allEntries = new ArrayList<>();
        for(OFBsnGentableDescStatsReply reply: replies) {
            allEntries.addAll(reply.getEntries());
        }
        return new GenTableMap(allEntries);
    }

    @Override
    public String toString() {
        return idMap.toString();
    }

    public int size() {
        return idMap.size();
    }
}
