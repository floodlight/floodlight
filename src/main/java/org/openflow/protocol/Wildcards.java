package org.openflow.protocol;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Joiner;

/**
 * a more user friendly representation of the wildcards bits in an OpenFlow
 * match. The Wildcards object is
 * <ul>
 * <li>immutable (i.e., threadsafe)</li>
 * <li>instance managed (don't instantiate it yourself), instead call "of"</li>
 * <ul>
 * <p>
 * You can construct a Wildcard object from either its integer representation
 * </p>
 * <code>
 *    Wildcard.of(0x3820e0);
 *  </code>
 * <p>
 * Or start with either an empty or full wildcard, and select/unselect foo.
 * </p>
 * <code>
 *  Wildcard w = Wildcards.NONE
 *                .set(Flag.DL_SRC, Flag. DL_DST, Flag.DL_VLAN_PCP)
 *                .setNwDstMask(8)
 *                .setNwSrcMask(8);
 *  </code>
 * <p>
 * <b>Remember:</b> Wildcards objects are immutable. set... operations have
 * <b>NO EFFECT</b> on the current wildcard object. You HAVE to use the returned
 * changed object.
 * </p>
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class Wildcards {

    public final static Wildcards FULL = new Wildcards(OFMatch.OFPFW_ALL_SANITIZED);

    public final static Wildcards EXACT = new Wildcards(0);

    // floodlight common case: matches on inport + l2
    public final static int INT_INPORT_L2_MATCH = 0x3820e0;
    public final static Wildcards INPORT_L2_MATCH = new Wildcards(
            INT_INPORT_L2_MATCH);

    /**
     * enum type for the binary flags that can be set in the wildcards field of
     * an OFMatch. Replaces the unwieldy c-ish int constants in OFMatch.
     */
    public static enum Flag {
        IN_PORT(OFMatch.OFPFW_IN_PORT),  /* Switch input port. */
        DL_VLAN(OFMatch.OFPFW_DL_VLAN), /* VLAN id. */
        DL_SRC(OFMatch.OFPFW_DL_SRC), /* Ethernet source address. */
        DL_DST(OFMatch.OFPFW_DL_DST), /* Ethernet destination addr */
        DL_TYPE(OFMatch.OFPFW_DL_TYPE), /* Ethernet frame type. */
        NW_PROTO(OFMatch.OFPFW_NW_PROTO), /* IP protocol. */
        TP_SRC(OFMatch.OFPFW_TP_SRC), /* TCP/UDP source port. */
        TP_DST(OFMatch.OFPFW_TP_DST), /* TCP/UDP destination port. */
        DL_VLAN_PCP(OFMatch.OFPFW_DL_VLAN_PCP), /* VLAN priority. */
        NW_TOS(OFMatch.OFPFW_NW_TOS); /* IP ToS (DSCP field, 6 bits). */

        final int bitPosition;

        Flag(int bitPosition) {
            this.bitPosition = bitPosition;
        }
    }

    private final int flags;

    /** private constructor. use Wildcard.of() instead */
    private Wildcards(int flags) {
        this.flags = flags;
    }

    /**
     * return a wildcard object matching the given int flags. May reuse / cache
     * frequently used wildcard instances. Don't rely on it though (use equals
     * not ==).
     *
     * @param flags
     * @return
     */
    public static Wildcards of(int flags) {
        switch(flags) {
            case 0x0000:
                return EXACT;
            case OFMatch.OFPFW_ALL:
            case OFMatch.OFPFW_ALL_SANITIZED:
                return FULL;
            case INT_INPORT_L2_MATCH:
                return INPORT_L2_MATCH;
            default:
                return new Wildcards(flags);
        }
    }

    /** convience method return a wildcard for exactly one set flag */
    public static Wildcards of(Wildcards.Flag setFlag) {
        return Wildcards.of(setFlag.bitPosition);
    }

    /** convience method return a wildcard for exactly two set flags */
    public static Wildcards of(Wildcards.Flag setFlag, Wildcards.Flag setFlag2) {
        return Wildcards.of(setFlag.bitPosition | setFlag2.bitPosition);
    }

    /** convience method return a wildcard for an arbitrary number of set flags */
    public static Wildcards of(Wildcards.Flag... setFlags) {
        int flags = 0;
        for (Wildcards.Flag flag : setFlags)
            flags |= flag.bitPosition;
        return Wildcards.of(flags);
    }

    /**
     * convience method return a wildcard for exactly one set flag and nwSrc/Dst
     * masks
     *
     * @param nwSrcCidrMask
     *            netmask for nw (IP) matches in <b>standard CIDR notation</b>
     * @param nwDstCidrMask
     *            netmask for nw (IP) matches in <b>standard CIDR notation</b>
     **/
    public static Wildcards of(Wildcards.Flag setFlag, int nwSrcCidrMask,
                               int nwDstCidrMask) {
        int flags = setFlag.bitPosition
                        | Math.max(0, 32 - nwSrcCidrMask) << OFMatch.OFPFW_NW_SRC_SHIFT
                        | Math.max(0, 32 - nwDstCidrMask) << OFMatch.OFPFW_NW_DST_SHIFT;
        return Wildcards.of(flags);
    }

    /**
     * convience method return a wildcard for exactly two set flags and
     * nwSrc/Dst masks
     *
     * @param nwSrcCidrMask
     *            netmask for nw (IP) matches in <b>standard CIDR notation</b>
     * @param nwDstCidrMask
     *            netmask for nw (IP) matches in <b>standard CIDR notation</b>
     **/
    public static Wildcards of(Wildcards.Flag setFlag, Wildcards.Flag setFlag2,
                               int nwSrcCidrMask, int nwDstCidrMask) {
        int flags =
                setFlag.bitPosition
                        | Math.max(0, 32 - nwSrcCidrMask) << OFMatch.OFPFW_NW_SRC_SHIFT
                        | Math.max(0, 32 - nwDstCidrMask) << OFMatch.OFPFW_NW_DST_SHIFT;
        return Wildcards.of(flags);
    }

    /**
     * convience method return a wildcard for a set of flags and nwSrc/Dst masks
     *
     * @param nwSrcCidrMask
     *            netmask for nw (IP) matches in <b>standard CIDR notation</b>
     * @param nwDstCidrMask
     *            netmask for nw (IP) matches in <b>standard CIDR notation</b>
     **/
    public static Wildcards of(Set<Flag> setFlags, int nwSrcCidrMask, int nwDstCidrMask) {
        int flags = 0;
        for (Wildcards.Flag flag : setFlags)
            flags |= flag.bitPosition;
        flags |= Math.max(0, 32 - nwSrcCidrMask) << OFMatch.OFPFW_NW_SRC_SHIFT
              | Math.max(0, 32 - nwDstCidrMask) << OFMatch.OFPFW_NW_DST_SHIFT;
        return Wildcards.of(flags);
    }

    /** is the given wildcard flag set */
    public boolean hasFlag(Wildcards.Flag flag) {
        return (flags & flag.bitPosition) != 0;
    }

    /**
     * return a Wildcards object that has the given flag set
     * <p>
     * <b>NOTE:</b> NOT a mutator function. 'this' wildcard object stays
     * unmodified. </b>
     */
    public Wildcards set(Wildcards.Flag flag) {
        int flags = this.flags | flag.bitPosition;
        if (flags == this.flags)
            return this;
        else
            return new Wildcards(flags);
    }

    /**
     * return a Wildcards object that has the given flags set
     * <p>
     * <b>NOTE:</b> NOT a mutator function. 'this' wildcard object stays
     * unmodified. </b>
     */
    public Wildcards set(Wildcards.Flag flag, Wildcards.Flag flag2) {
        int flags = this.flags | flag.bitPosition | flag2.bitPosition;
        if (flags == this.flags)
            return this;
        else
            return new Wildcards(flags);
    }

    /**
     * return a Wildcards object that has the given flags set
     * <p>
     * <b>NOTE:</b> NOT a mutator function. 'this' wildcard object stays
     * unmodified. </b>
     */
    public Wildcards set(Wildcards.Flag... setFlags) {
        int flags = this.flags;
        for (Wildcards.Flag flag : setFlags)
            flags |= flag.bitPosition;
        if (flags == this.flags)
            return this;
        else
            return new Wildcards(flags);
    }

    /**
     * return a Wildcards object that has the given flags unset
     * <p>
     * <b>NOTE:</b> NOT a mutator function. 'this' wildcard object stays
     * unmodified. </b>
     */
    public Wildcards unset(Wildcards.Flag flag) {
        int flags = this.flags & ~flag.bitPosition;
        if (flags == this.flags)
            return this;
        else
            return new Wildcards(flags);
    }

    /**
     * return a Wildcards object that has the given flags unset
     * <p>
     * <b>NOTE:</b> NOT a mutator function. 'this' wildcard object stays
     * unmodified. </b>
     */
    public Wildcards unset(Wildcards.Flag flag, Wildcards.Flag flag2) {
        int flags = this.flags & ~flag.bitPosition & ~flag2.bitPosition;
        if (flags == this.flags)
            return this;
        else
            return new Wildcards(flags);
    }

    /**
     * return a Wildcards object that has the given flags unset
     * <p>
     * <b>NOTE:</b> NOT a mutator function. 'this' wildcard object stays
     * unmodified. </b>
     */
    public Wildcards unset(Wildcards.Flag... setFlags) {
        int flags = this.flags;
        for (Wildcards.Flag flag : setFlags)
            flags &= ~flag.bitPosition;
        if (flags == this.flags)
            return this;
        else
            return new Wildcards(flags);
    }

    /**
     * return the nw src mask in normal CIDR style, e.g., 8 means x.x.x.x/8
     * means 8 bits wildcarded
     */
    public int getNwSrcMask() {
        return Math.max(
                0,
                32 - ((flags & OFMatch.OFPFW_NW_SRC_MASK) >> OFMatch.OFPFW_NW_SRC_SHIFT));
    }

    /**
     * return the nw dst mask in normal CIDR style, e.g., 8 means x.x.x.x/8
     * means 8 bits wildcarded
     */
    public int getNwDstMask() {
        return Math.max(
                0,
                32 - ((flags & OFMatch.OFPFW_NW_DST_MASK) >> OFMatch.OFPFW_NW_DST_SHIFT));
    }

    /**
     * return a Wildcard object that has the given nwSrcCidrMask set.
     * <b>NOTE:</b> NOT a mutator function. 'this' wildcard object stays
     * unmodified. </b>
     *
     * @param srcCidrMask
     *            source mask to set in <b>normal CIDR notation</b>, i.e., 8
     *            means x.x.x.x/8
     * @return a modified object
     */
    public Wildcards setNwSrcMask(int srcCidrMask) {
        int flags =
                this.flags
                        & ~OFMatch.OFPFW_NW_SRC_MASK
                        | (Math.max(0, 32 - srcCidrMask)) << OFMatch.OFPFW_NW_SRC_SHIFT;
        if (flags == this.flags)
            return this;
        else
            return new Wildcards(flags);
    }

    /**
     * return a Wildcard object that has the given nwDstCidrMask set.
     * <b>NOTE:</b> NOT a mutator function. 'this' wildcard object stays
     * unmodified. </b>
     *
     * @param dstCidrMask
     *            dest mask to set in <b>normal CIDR notation</b>, i.e., 8 means
     *            x.x.x.x/8
     * @return a modified object
     */
    public Wildcards setNwDstMask(int dstCidrMask) {
        int flags =
                this.flags
                        & ~OFMatch.OFPFW_NW_DST_MASK
                        | (Math.max(0, 32 - dstCidrMask)) << OFMatch.OFPFW_NW_DST_SHIFT;
        if (flags == this.flags)
            return this;
        else
            return new Wildcards(flags);
    }

    /**
     * return a Wildcard object that is inverted to this wildcard object.
     * <b>NOTE:</b> NOT a mutator function. 'this' wildcard object stays
     * unmodified. </b>
     * @return a modified object
     */
    public Wildcards inverted() {
        return Wildcards.of(getIntSanitized() ^ OFMatch.OFPFW_ALL_SANITIZED);
    }

    /**
     * return the binary wildcard flags as an EnumSet. Do not modify.
     *
     * @return a modified object
     */
    public EnumSet<Wildcards.Flag> getFlags() {
        EnumSet<Wildcards.Flag> res = EnumSet.noneOf(Wildcards.Flag.class);
        for (Wildcards.Flag flag : Flag.values()) {
            if ((flags & flag.bitPosition) != 0) {
                res.add(flag);
            }
        }
        return res;
    }

    /** return the OpenFlow 'wire' integer representation of these wildcards */
    public int getInt() {
        return flags;
    }

    /**
     * return the OpenFlow 'wire' integer representation of these wildcards.
     * Sanitize nw_src and nw_dst to be max. 32 (values > 32 are technically
     * possible, but don't make semantic sense)
     */
    public int getIntSanitized() {
        int flags = this.flags;
        if (((flags & OFMatch.OFPFW_NW_SRC_MASK) >> OFMatch.OFPFW_NW_SRC_SHIFT) > 32) {
            flags = (flags & ~OFMatch.OFPFW_NW_SRC_MASK) | OFMatch.OFPFW_NW_SRC_ALL;
        }
        if (((flags & OFMatch.OFPFW_NW_DST_MASK) >> OFMatch.OFPFW_NW_DST_SHIFT) > 32) {
            flags = (flags & ~OFMatch.OFPFW_NW_DST_MASK) | OFMatch.OFPFW_NW_DST_ALL;
        }
        return flags;
    }

    /**
     * is this a wildcard set that has all flags set + and full (/0) nw_src and
     * nw_dst wildcarding ?
     */
    public boolean isFull() {
        return flags == OFMatch.OFPFW_ALL || flags == OFMatch.OFPFW_ALL_SANITIZED;
    }

    /** is this a wildcard of an exact match */
    public boolean isExact() {
        return flags == 0;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + flags;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Wildcards other = (Wildcards) obj;
        if (flags != other.flags)
            return false;
        return true;
    }

    private final static Joiner pipeJoiner = Joiner.on("|");

    @Override
    public String toString() {
        List<String> res = new ArrayList<String>();
        for (Wildcards.Flag flag : Flag.values()) {
            if ((flags & flag.bitPosition) != 0) {
                res.add(flag.name().toLowerCase());
            }
        }
        int nwSrcMask = getNwSrcMask();
        if (nwSrcMask < 32) {
            res.add("nw_src(/" + nwSrcMask + ")");
        }
        int nwDstMask = getNwDstMask();
        if (nwDstMask < 32) {
            res.add("nw_dst(/" + nwDstMask + ")");
        }

        return pipeJoiner.join(res);
    }

}