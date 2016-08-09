package net.floodlightcontroller.util;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFGroupAdd;
import org.projectfloodlight.openflow.protocol.OFGroupDelete;
import org.projectfloodlight.openflow.protocol.OFGroupMod;
import org.projectfloodlight.openflow.protocol.OFGroupModify;
import org.projectfloodlight.openflow.protocol.OFGroupType;
import org.projectfloodlight.openflow.types.OFGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.databind.MappingJsonFactory;

/**
 * Convert OFGroups to and from JSON strings. Used primarily by 
 * the static flow pusher to store and retrieve flow entries.
 * 
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 *
 */
public class GroupUtils {
    private static final Logger log = LoggerFactory.getLogger(GroupUtils.class);

    public static final String GROUP_ID = "group_id";
    public static final String GROUP_ID_MAX = "max";
    public static final String GROUP_ID_ANY = "any";
    public static final String GROUP_ID_ALL = "all";

    public static final String GROUP_TYPE = "group_type";
    public static final String GROUP_TYPE_FF = "fast_failover";
    public static final String GROUP_TYPE_ALL = "all";
    public static final String GROUP_TYPE_SELECT = "select";
    public static final String GROUP_TYPE_INDIRECT = "indirect";

    public static final String GROUP_BUCKETS = "group_buckets";
    public static final String BUCKET_ID = "bucket_id";
    public static final String BUCKET_WEIGHT = "bucket_weight";
    public static final String BUCKET_WATCH_PORT = "bucket_watch_port";
    public static final String BUCKET_WATCH_GROUP = "bucket_watch_group";
    public static final String BUCKET_ACTIONS = "bucket_actions";

    private static final JsonFactory jsonFactory = new JsonFactory();

    private static final String JSON_EMPTY_ARRAY = "[]";
    private static final String JSON_EMPTY_VALUE = "";

    private GroupUtils() { }

    public static OFGroupModify toGroupModify(OFGroupMod gm) {
        return OFFactories.getFactory(gm.getVersion()).buildGroupModify()
                .setBuckets(gm.getBuckets())
                .setGroup(gm.getGroup())
                .setGroupType(gm.getGroupType())
                .setXid(gm.getXid())
                .build();
    }

    public static OFGroupDelete toGroupDelete(OFGroupMod gm) {
        return OFFactories.getFactory(gm.getVersion()).buildGroupDelete()
                /* don't care about buckets or type */
                .setGroup(gm.getGroup())
                .setGroupType(gm.getGroupType())
                .setXid(gm.getXid())
                .build();
    }

    public static OFGroupAdd toGroupAdd(OFGroupMod gm) {
        return OFFactories.getFactory(gm.getVersion()).buildGroupAdd()
                .setBuckets(gm.getBuckets())
                .setGroup(gm.getGroup())
                .setGroupType(gm.getGroupType())
                .setXid(gm.getXid())
                .build();
    }

    public static boolean setGroupIdFromString(OFGroupMod.Builder g, String s) {
        if (g == null) {
            throw new IllegalArgumentException("OFGroupMod cannot be null");
        }
        if (s == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        OFGroup group = groupIdFromString(s);
        if (group == null) {
            log.error("Could not set group ID {} due to parse error", s);
            return false;
        } else {
            g.setGroup(group);
            return true;
        }	
    }

    public static OFGroup groupIdFromString(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String cannot be null");
        }
        s = s.trim().toLowerCase();
        try {
            if (s.equals(GROUP_ID_ALL)) {
                return OFGroup.ALL;
            } else if (s.equals(GROUP_ID_ANY)) {
                return OFGroup.ANY;
            } else if (s.equals(GROUP_ID_MAX)) {
                return OFGroup.MAX;
            } else {
                return OFGroup.of(ParseUtils.parseHexOrDecInt(s));
            }
        } catch (Exception e) {
            log.error("Could not parse group ID {}", s);
            return null;
        }
    }

    public static String groupIdToString(OFGroup g) {
        if (g == null) {
            throw new IllegalArgumentException("Group ID cannot be null");
        }
        if (g.equals(OFGroup.ALL)) {
            return GROUP_ID_ALL;
        }
        if (g.equals(OFGroup.ANY)) {
            return GROUP_ID_ANY;
        }
        if (g.equals(OFGroup.MAX)) {
            return GROUP_ID_MAX;
        }
        return Integer.toString(g.getGroupNumber());
    }

    public static OFGroupType groupTypeFromString(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String cannot be null");
        }

        s = s.trim().toLowerCase();

        switch (s) {
        case GROUP_TYPE_ALL:
            return OFGroupType.ALL;
        case GROUP_TYPE_FF:
            return OFGroupType.FF;
        case GROUP_TYPE_INDIRECT:
            return OFGroupType.INDIRECT;
        case GROUP_TYPE_SELECT:
            return OFGroupType.SELECT;
        default:
            log.error("Unrecognized group type {}", s);
            return null;
        }
    }

    public static boolean setGroupTypeFromString(OFGroupMod.Builder g, String s) {
        if (g == null) {
            throw new IllegalArgumentException("OFGroupMod cannot be null");
        }

        OFGroupType t = groupTypeFromString(s);
        if (t != null) {
            g.setGroupType(t);
            return true;
        }
        return false;
    }

    public static String groupTypeToString(OFGroupType t) {
        if (t == null) {
            throw new IllegalArgumentException("OFGroupType cannot be null");
        }

        switch (t) {
        case ALL:
            return GROUP_TYPE_ALL;
        case FF:
            return GROUP_TYPE_FF;
        case INDIRECT:
            return GROUP_TYPE_INDIRECT;
        case SELECT:
            return GROUP_TYPE_SELECT;
        default:
            log.error("Unrecognized group type {}", t);
            return JSON_EMPTY_VALUE;
        }
    }

    /**
     * Append an array of buckets to an existing JsonGenerator.
     * This method assumes the field name of the array has been
     * written already, if required. The appended data will
     * be formatted as follows:
     *   [
     *     {
     *       bucket-1
     *     },
     *     {
     *       bucket-2
     *     },
     *     ...,
     *     {
     *       bucket-n
     *     }
     *   ]
     * @param jsonGen
     * @param bucketList
     */
    public static void groupBucketsToJsonArray(JsonGenerator jsonGen, List<OFBucket> bucketList) {
        jsonGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true);

        try {
            int bucketId = 1;
            jsonGen.writeStartArray();
            for (OFBucket b : bucketList) {
                jsonGen.writeStartObject();
                jsonGen.writeNumberField(BUCKET_ID, bucketId++); /* not preserved from original, but indicates order */
                jsonGen.writeStringField(BUCKET_WATCH_GROUP, groupIdToString(b.getWatchGroup()));
                jsonGen.writeStringField(BUCKET_WATCH_PORT, MatchUtils.portToString(b.getWatchPort()));
                jsonGen.writeNumberField(BUCKET_WEIGHT, b.getWeight());
                jsonGen.writeStringField(BUCKET_ACTIONS, ActionUtils.actionsToString(b.getActions())); /* TODO update to object array */
                jsonGen.writeEndObject();
            }
            jsonGen.writeEndArray();
        } catch (IOException e) {
            log.error("Error composing group bucket JSON array. {}", e.getMessage());
            return;
        }
    }

    /**
     * Convert a list of group buckets into a JSON-formatted
     * string. The string data will be formatted as follows:
     *   [
     *     {
     *       bucket-1
     *     },
     *     {
     *       bucket-2
     *     },
     *     ...,
     *     {
     *       bucket-n
     *     }
     *   ]
     * @param bucketList
     * @return the string, formatted as described above
     */
    public static String groupBucketsToJsonArray(List<OFBucket> bucketList) {
        Writer w = new StringWriter();
        JsonGenerator jsonGen;
        try {
            jsonGen = jsonFactory.createGenerator(w);
        } catch (IOException e) {
            log.error("Could not instantiate JSON Generator. {}", e.getMessage());
            return JSON_EMPTY_ARRAY;
        }

        groupBucketsToJsonArray(jsonGen, bucketList);

        return w.toString(); /* overridden impl returns contents of Writer's StringBuffer */
    }

    /**
     * Convert a JSON-formatted string of group buckets to a
     * Java list of buckets. The JSON format expected is:
     *   [
     *     {
     *       bucket-1
     *     },
     *     {
     *       bucket-2
     *     },
     *     ...,
     *     {
     *       bucket-n
     *     }
     *   ]
     * @param g the group-mod message to add the buckets
     * @param json the string, formatted as described above
     */
    public static boolean setGroupBucketsFromJsonArray(OFGroupMod.Builder g, String json) {
        final Map<Integer, OFBucket> bucketsById = new HashMap<Integer, OFBucket>();
        final MappingJsonFactory f = new MappingJsonFactory();

        if (g == null) {
            throw new IllegalArgumentException("OFGroupMod cannot be null");
        }
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }

        final JsonParser jp;
        try {
            jp = f.createParser(json);
        } catch (IOException e) {
            log.error("Could not create JSON parser for {}", json);
            return false;
        }

        try {
            if (jp.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Expected START_ARRAY");
            }

            while (jp.nextToken() != JsonToken.END_ARRAY) {
                if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
                    throw new IOException("Expected START_OBJECT");
                }

                OFBucket.Builder b = OFFactories.getFactory(g.getVersion()).buildBucket();
                int bucketId = -1;

                while (jp.nextToken() != JsonToken.END_OBJECT) {
                    if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                        throw new IOException("Expected FIELD_NAME");
                    }

                    String key = jp.getCurrentName().toLowerCase().trim();
                    jp.nextToken();
                    String value = jp.getText().toLowerCase().trim();
                    switch (key) {
                    case BUCKET_ID:
                        bucketId = ParseUtils.parseHexOrDecInt(value);
                        break;
                    case BUCKET_WATCH_GROUP:
                        b.setWatchGroup(groupIdFromString(value));
                        break;
                    case BUCKET_WATCH_PORT:
                        b.setWatchPort(MatchUtils.portFromString(value));
                        break;
                    case BUCKET_WEIGHT:
                        b.setWeight(ParseUtils.parseHexOrDecInt(value));
                        break;
                    case BUCKET_ACTIONS:
                        b.setActions(ActionUtils.fromString(value, b.getVersion())); // TODO update to JSON 
                        break;
                    default:
                        log.warn("Unknown bucket key {}", key);
                        break;
                    }
                }
                if (bucketId != -1) {
                    bucketsById.put(bucketId, b.build());
                } else {
                    log.error("Must provide a bucket ID for bucket {}", b);
                }
            }
        } catch (IOException e) {
            log.error("Could not parse: {}", json);
            log.error("JSON parse error message: {}", e.getMessage());
            return false;
        }

        g.setBuckets(
                bucketsById.entrySet()
                .stream()
                .sorted( /* invert to get sorted smallest to largest */
                        (e1, e2) -> Integer.compare(e1.getKey(), e2.getKey())
                        )
                .map(Map.Entry::getValue)
                .collect(Collectors.toList())
                );
        return true;
    }
}