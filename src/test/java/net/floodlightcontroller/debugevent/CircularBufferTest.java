package net.floodlightcontroller.debugevent;

import java.util.ArrayList;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.test.FloodlightTestCase;

public class CircularBufferTest extends FloodlightTestCase {
    CircularBuffer<String> cb;
    protected static Logger log = LoggerFactory.getLogger(CircularBufferTest.class);

    @Test
    public void testCircularNature() {
        cb = new CircularBuffer<String>(2);
        cb.add("String 1");
        assertEquals(1, cb.size());
        cb.add("String 2");
        assertEquals(2, cb.size());
        cb.add("String 3");
        assertEquals(2, cb.size());

        for (String s : cb) {
            assertEquals(false, s.contains("1"));
        }
    }

    class Elems {
        String str;
        Boolean boo;

        public Elems(String s,boolean b) {
            this.str = s;
            this.boo = b;
        }
    }

    @Test
    public void testAdd() {
        CircularBuffer<Elems> eb = new CircularBuffer<Elems>(2);
        Elems theone = new Elems("String 1", false);
        Elems ret1 = eb.add(theone);
        assertEquals(null, ret1);
        Elems ret2 = eb.add(new Elems("String 2", true));
        assertEquals(null, ret2);
        Elems ret3 = eb.add(new Elems("String 3", true));
        // We want to see if what is returned is a reference to the original object
        // 'theone'. So we use  '==' to compare the references
        assertEquals(true, ret3 == theone);
        log.info("{} {}", ret3, theone);
    }

    @Test
    public void testAddAll() {
        CircularBuffer<Elems> eb = new CircularBuffer<Elems>(2);
        Elems one = new Elems("String 1", false);
        eb.add(one);
        ArrayList<Elems> elist = new ArrayList<Elems>();
        Elems two = new Elems("String 2", true);
        elist.add(two);
        Elems three = new Elems("String 3", true);
        elist.add(three);
        Elems four = new Elems("String 4", true);
        elist.add(four);

        ArrayList<Elems> retlist = eb.addAll(elist, 2);
        assertEquals(null, retlist.get(0));
        assertEquals(true, retlist.get(1) == one);
        assertEquals(true, retlist.get(2) == four);

        ArrayList<Elems> retlist2 = eb.addAll(retlist, 3);
        assertEquals(null, retlist2.get(0));
        assertEquals(true, retlist2.get(1) == two);
        assertEquals(true, retlist2.get(2) == three);

        ArrayList<Elems> retlist3 = eb.addAll(retlist2, 4);
        assertEquals(retlist3, retlist2);

    }

}
