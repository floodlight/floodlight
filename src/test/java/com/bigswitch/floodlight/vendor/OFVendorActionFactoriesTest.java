package com.bigswitch.floodlight.vendor;

import static org.junit.Assert.assertEquals;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Test;
import org.openflow.protocol.action.OFActionVendor;
import org.openflow.protocol.factory.OFVendorActionFactory;

public class OFVendorActionFactoriesTest {

    @Test
    public void testNiciraVendorActionFactory() {
        OFNiciraVendorActionFactory factory =
                new OFNiciraVendorActionFactory();

        OFActionNiciraTtlDecrement ttl = new OFActionNiciraTtlDecrement();
        rereadAndCheck(factory, ttl);
    }

    @Test
    public void testBSNVendorActionFactory() {
        OFBigSwitchVendorActionFactory factory =
                new OFBigSwitchVendorActionFactory();

        OFActionMirror mirror = new OFActionMirror((short) 12);
        mirror.setCopyStage((byte) 96);
        mirror.setDestPort(123);
        mirror.setVlanTag(42);
        rereadAndCheck(factory, mirror);

        OFActionTunnelDstIP dstIP = new OFActionTunnelDstIP((short) 12);
        dstIP.setTunnelDstIP(0x01020304);
        rereadAndCheck(factory, dstIP);

    }


    protected void rereadAndCheck(OFVendorActionFactory factory, OFActionVendor action) {
        ChannelBuffer buf= ChannelBuffers.buffer(action.getLengthU());
        action.writeTo(buf);
        OFActionVendor readAction = factory.readFrom(buf);
        assertBeansEqual(action, readAction);
    }

    public void assertBeansEqual(Object expected, Object actual) {
        BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(expected.getClass());
            for (PropertyDescriptor propertyDesc : beanInfo.getPropertyDescriptors()) {
                Object srcValue = propertyDesc.getReadMethod().invoke(expected);
                Object dstValue = propertyDesc.getReadMethod().invoke(actual);
                assertEquals("Bean Value: "+propertyDesc.getName() + " expected: "+srcValue + " != actual: "+dstValue, srcValue, dstValue);
            }
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
