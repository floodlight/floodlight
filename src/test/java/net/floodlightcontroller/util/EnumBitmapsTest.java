package net.floodlightcontroller.util;

import static org.junit.Assert.*;

import java.util.EnumSet;

import org.junit.Test;

import net.floodlightcontroller.util.EnumBitmaps.BitmapableEnum;

public class EnumBitmapsTest {

    private enum ColorEnum implements BitmapableEnum {
        RED (1 << 0),
        GREEN (1 << 1),
        BLUE ( 1 << 3); // note (1<<2) is missing

        private int value;

        private ColorEnum(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }
    }

    private enum ExtremeEnum implements BitmapableEnum {
        SMALL (1 << 0),
        BIG ( 1 << 31);

        private int value;

        private ExtremeEnum(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }

    }

    private enum InvalidEnum implements BitmapableEnum {
        FOO_VALID (0x1),
        BAR_INVALID (0x6); // Error: more than one bit set

        private int value;

        private InvalidEnum(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }
    }

    private enum InvalidEnum2 implements BitmapableEnum {
        FOOFOO_INVALID (0x0), // error: no bit set
        BARBAR_VALID (0x1);

        private int value;

        private InvalidEnum2(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }
    }

    @Test
    public void testNormalBehavior() {
        EnumSet<ColorEnum> set = null;
        int bitmap = 0;

        // With color enum.
        bitmap = 0;
        set = EnumBitmaps.toEnumSet(ColorEnum.class, bitmap);
        assertEquals(EnumSet.noneOf(ColorEnum.class), set);
        assertEquals(bitmap, EnumBitmaps.toBitmap(set));

        bitmap = ColorEnum.RED.getValue();
        set = EnumBitmaps.toEnumSet(ColorEnum.class, bitmap);
        assertEquals(EnumSet.of(ColorEnum.RED), set);
        assertEquals(bitmap, EnumBitmaps.toBitmap(set));

        bitmap = ColorEnum.BLUE.getValue();
        set = EnumBitmaps.toEnumSet(ColorEnum.class, bitmap);
        assertEquals(EnumSet.of(ColorEnum.BLUE), set);
        assertEquals(bitmap, EnumBitmaps.toBitmap(set));

        bitmap = ColorEnum.RED.getValue() | ColorEnum.GREEN.getValue();
        set = EnumBitmaps.toEnumSet(ColorEnum.class, bitmap);
        assertEquals(EnumSet.of(ColorEnum.RED, ColorEnum.GREEN), set);
        assertEquals(bitmap, EnumBitmaps.toBitmap(set));

        bitmap = ColorEnum.RED.getValue() | ColorEnum.GREEN.getValue() |
                ColorEnum.BLUE.getValue();
        set = EnumBitmaps.toEnumSet(ColorEnum.class, bitmap);
        assertEquals(EnumSet.of(ColorEnum.RED, ColorEnum.GREEN, ColorEnum.BLUE),
                     set);
        assertEquals(bitmap, EnumBitmaps.toBitmap(set));

        assertEquals(0xb, EnumBitmaps.getMask(ColorEnum.class));

        // with extreme enum. Make sure 1 << 31 is handled correctly
        bitmap = 1 << 31;
        EnumSet<ExtremeEnum> extremeSet =
                EnumBitmaps.toEnumSet(ExtremeEnum.class, bitmap);
        assertEquals(EnumSet.of(ExtremeEnum.BIG), extremeSet);
        assertEquals(bitmap, EnumBitmaps.toBitmap(extremeSet));

        bitmap = (1 << 31) | (1 << 0);
        extremeSet = EnumBitmaps.toEnumSet(ExtremeEnum.class, bitmap);
        assertEquals(EnumSet.of(ExtremeEnum.BIG, ExtremeEnum.SMALL), extremeSet);
        assertEquals(bitmap, EnumBitmaps.toBitmap(extremeSet));

        assertEquals(0x80000001, EnumBitmaps.getMask(ExtremeEnum.class));

        // there are some cases were InvalidEnum's can actually be used.
        // It's fine if a developer chooses to change the behavior and make
        // the cases below fail!
        EnumSet<InvalidEnum> s1 = EnumSet.of(InvalidEnum.FOO_VALID);
        assertEquals(InvalidEnum.FOO_VALID.getValue(),
                     EnumBitmaps.toBitmap(s1));
        EnumSet<InvalidEnum2> s2 = EnumSet.of(InvalidEnum2.BARBAR_VALID);
        assertEquals(InvalidEnum2.BARBAR_VALID.getValue(),
                     EnumBitmaps.toBitmap(s2));
    }


    @Test
    public void testExceptions() {
        // Exception when using an invalid enum
        try {
            EnumBitmaps.toEnumSet(InvalidEnum.class, 0);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) {  }
        try {
            EnumBitmaps.toEnumSet(InvalidEnum.class, 1);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) {  }
        try {
            EnumBitmaps.getMask(InvalidEnum.class);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) {  }
        try {
            EnumSet<InvalidEnum> set = EnumSet.allOf(InvalidEnum.class);
            EnumBitmaps.toBitmap(set);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) { }
        try {
            EnumSet<InvalidEnum> set = EnumSet.of(InvalidEnum.BAR_INVALID);
            EnumBitmaps.toBitmap(set);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) { }

        // Again with a different one
        // Exception when using an invalid enum
        try {
            EnumBitmaps.toEnumSet(InvalidEnum2.class, 0);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) {  }
        try {
            EnumBitmaps.toEnumSet(InvalidEnum2.class, 1);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) {  }
        try {
            EnumBitmaps.getMask(InvalidEnum2.class);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) {  }
        try {
            EnumSet<InvalidEnum2> set = EnumSet.allOf(InvalidEnum2.class);
            EnumBitmaps.toBitmap(set);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) { }
        try {
            EnumSet<InvalidEnum2> set = EnumSet.of(InvalidEnum2.FOOFOO_INVALID);
            EnumBitmaps.toBitmap(set);
            fail("Expected exception not thrown");
        } catch (IllegalArgumentException e) { }

        // NPEs
        try {
            EnumBitmaps.<ColorEnum>toEnumSet(null, 0);
            fail("Expected exception not thrown");
        } catch (NullPointerException e) {  }
        try {
            EnumBitmaps.<ColorEnum>getMask(null);
            fail("Expected exception not thrown");
        } catch (NullPointerException e) {  }
        try {
            EnumBitmaps.<ColorEnum>toBitmap(null);
            fail("Expected exception not thrown");
        } catch (NullPointerException e) {  }

        // Bits set that aren't covered by the enum
        try {
            EnumBitmaps.<ColorEnum>toEnumSet(ColorEnum.class, 1 << 23);
            fail("Expected exception not thrown");
        } catch(IllegalArgumentException e) { }
    }

}
