/**
 * 
 */
package net.floodlightcontroller.devicemanager.test;

import static net.floodlightcontroller.devicemanager.IDeviceService.DeviceField.MAC;
import static net.floodlightcontroller.devicemanager.IDeviceService.DeviceField.PORT;
import static net.floodlightcontroller.devicemanager.IDeviceService.DeviceField.SWITCH;
import static net.floodlightcontroller.devicemanager.IDeviceService.DeviceField.VLAN;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.IEntityClass;
import net.floodlightcontroller.devicemanager.IDeviceService.DeviceField;
import net.floodlightcontroller.devicemanager.internal.DefaultEntityClassifier;
import net.floodlightcontroller.devicemanager.internal.Entity;

/**
 * Extension to simple entity classifier to help in unit tests to provide table
 * based multiple entity classification mock for reclassification tests
 *
 */
public class MockFlexEntityClassifier extends DefaultEntityClassifier {
	Map <Long, IEntityClass> switchEntities;
	Map <Short, IEntityClass> vlanEntities;
	
    public static class TestEntityClass implements IEntityClass {
    	String name;
    	public TestEntityClass(String name) {
    		this.name = name;
    	}
        @Override
        public EnumSet<DeviceField> getKeyFields() {
            return EnumSet.of(MAC);
        }

        @Override
        public String getName() {
            return name;
        }
    }
    public static IEntityClass defaultClass = new TestEntityClass("default");
    public MockFlexEntityClassifier() {
    	switchEntities = new HashMap<Long, IEntityClass> ();
    	vlanEntities = new HashMap<Short, IEntityClass> ();
    }
    public IEntityClass createTestEntityClass(String name) {
    	return new TestEntityClass(name);
    }
    
    public void addSwitchEntity(Long dpid, IEntityClass entityClass) {
    	switchEntities.put(dpid, entityClass);
    }
    public void removeSwitchEntity(Long dpid) {
    	switchEntities.remove(dpid);
    }
    public void addVlanEntities(Short vlan, IEntityClass entityClass) {
    	vlanEntities.put(vlan, entityClass);
    }
    public void removeVlanEntities(Short vlan) {
    	vlanEntities.remove(vlan);
    }
    @Override
    public IEntityClass classifyEntity(Entity entity) {
        if (switchEntities.containsKey(entity.getSwitchDPID()))
        	return switchEntities.get(entity.getSwitchDPID());
        if (vlanEntities.containsKey(entity.getVlan()))
        	return vlanEntities.get(entity.getVlan());
        return defaultClass;
    }
    @Override
    public EnumSet<IDeviceService.DeviceField> getKeyFields() {
        return EnumSet.of(MAC, VLAN, SWITCH, PORT);
    }
}
