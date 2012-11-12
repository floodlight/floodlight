package org.openflow.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;

import org.junit.Test;
import org.openflow.protocol.Wildcards.Flag;

public class WildcardsTest {

    @Test
    public void testBasic() {
        int[] intMasks = { 0, 0x3820e0, OFMatch.OFPFW_ALL_SANITIZED };
        for (int i : intMasks) {
            Wildcards w = Wildcards.of(i);
            assertEquals(i, w.getInt());
        }
    }

    @Test
    public void testAllSanitize() {
        Wildcards w = Wildcards.of(OFMatch.OFPFW_ALL);
        assertEquals(OFMatch.OFPFW_ALL_SANITIZED, w.getInt());
        assertTrue(w.isFull());
        assertFalse(w.isExact());
    }

    @Test
    public void testAll() {
        Wildcards all = Wildcards.FULL;
        assertTrue(all.isFull());
        assertFalse(all.isExact());
        assertEquals(0, all.getNwDstMask());
        assertEquals(0, all.getNwSrcMask());

        // unsetting flags from NONE is a no-op
        Wildcards stillAll = all.set(Flag.IN_PORT);
        assertTrue(stillAll.isFull());
        assertEquals(all, stillAll);

        // so is setting a >= 32 netmask

        stillAll = all.setNwSrcMask(0);
        assertTrue(stillAll.isFull());
        assertEquals(all, stillAll);

        stillAll = all.setNwDstMask(0);
        assertTrue(stillAll.isFull());
        assertEquals(all, stillAll);
    }

    @Test
    public void testNone() {
        Wildcards none = Wildcards.EXACT;
        assertTrue(none.isExact());
        assertEquals(32, none.getNwDstMask());
        assertEquals(32, none.getNwSrcMask());

        // unsetting flags from NONE is a no-op
        Wildcards stillNone = none.unset(Flag.IN_PORT);
        assertTrue(stillNone.isExact());
        assertEquals(none, stillNone);

        // so is setting a >= 32 netmask
        stillNone = none.setNwSrcMask(32);
        assertTrue(stillNone.isExact());
        assertEquals(none, stillNone);

        stillNone = none.setNwDstMask(32);
        assertTrue(stillNone.isExact());
        assertEquals(none, stillNone);
    }

    @Test
    public void testSetOneFlag() {
        Wildcards none = Wildcards.EXACT;
        assertTrue(none.isExact());
        assertFalse(none.hasFlag(Flag.DL_SRC));
        Wildcards one = none.set(Flag.DL_SRC);
        assertFalse(one.isExact());
        assertTrue(one.hasFlag(Flag.DL_SRC));
        assertEquals(OFMatch.OFPFW_DL_SRC, one.getInt());
        assertEquals(EnumSet.of(Flag.DL_SRC), one.getFlags());
    }

    @Test
    public void testSetTwoFlags() {
        Wildcards none = Wildcards.EXACT;

        // set two flags
        Wildcards two = none.set(Flag.DL_SRC, Flag.DL_DST);
        assertFalse(two.isExact());
        assertTrue(two.hasFlag(Flag.DL_SRC));
        assertTrue(two.hasFlag(Flag.DL_DST));
        assertEquals(OFMatch.OFPFW_DL_SRC | OFMatch.OFPFW_DL_DST, two.getInt());
        assertEquals(EnumSet.of(Flag.DL_SRC, Flag.DL_DST), two.getFlags());

        // unset dl_dst
        Wildcards gone = two.unset(Flag.DL_DST);
        assertFalse(gone.isExact());
        assertTrue(gone.hasFlag(Flag.DL_SRC));
        assertFalse(gone.hasFlag(Flag.DL_DST));
        assertEquals(OFMatch.OFPFW_DL_SRC, gone.getInt());
        assertEquals(EnumSet.of(Flag.DL_SRC), gone.getFlags());
    }

    @Test
    public void testSetNwSrc() {
        Wildcards none = Wildcards.EXACT;
        assertEquals(32, none.getNwSrcMask());

        // unsetting flags from NONE is a no-op
        Wildcards nwSet = none.setNwSrcMask(8);
        assertFalse(nwSet.isExact());
        assertEquals(EnumSet.noneOf(Flag.class), nwSet.getFlags());
        assertEquals(8, nwSet.getNwSrcMask());
        assertEquals((32 - 8) << OFMatch.OFPFW_NW_SRC_SHIFT, nwSet.getInt());
    }

    @Test
    public void testSetNwDst() {
        Wildcards none = Wildcards.EXACT;
        assertEquals(32, none.getNwDstMask());

        // unsetting flags from NONE is a no-op
        Wildcards nwSet = none.setNwDstMask(8);
        assertFalse(nwSet.isExact());
        assertEquals(EnumSet.noneOf(Flag.class), nwSet.getFlags());
        assertEquals(8, nwSet.getNwDstMask());
        assertEquals((32 - 8) << OFMatch.OFPFW_NW_DST_SHIFT, nwSet.getInt());
    }

    @Test
    public void testToString() {
        String s = Wildcards.FULL.toString();
        assertNotNull(s);
        assertTrue(s.length() > 0);
    }

    @Test
    public void testInvert() {
        assertEquals(Wildcards.FULL, Wildcards.EXACT.inverted());

        Wildcards some = Wildcards.of(Flag.DL_VLAN, Flag.DL_VLAN_PCP);
        Wildcards inv = some.inverted();

        for(Flag f : Flag.values()) {
            assertEquals( f == Flag.DL_VLAN || f == Flag.DL_VLAN_PCP, some.hasFlag(f));
            assertEquals(!(f == Flag.DL_VLAN || f == Flag.DL_VLAN_PCP), inv.hasFlag(f));
        }
        assertEquals(0, inv.getNwDstMask());
        assertEquals(0, inv.getNwSrcMask());
    }
}
