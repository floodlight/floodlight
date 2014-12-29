/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
*
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.core.internal;

import static org.easymock.EasyMock.anyObject;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import net.floodlightcontroller.test.FloodlightTestCase;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;


import org.junit.After;

import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.internal.Controller.IUpdate;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.MockDebugCounterService;

public class RoleManagerTest extends FloodlightTestCase {
    private Controller controller;
    private RoleManager roleManager;

    @Override
    @Before
    public void setUp() throws Exception {
        doSetUp(HARole.ACTIVE);
    }

    private void doSetUp(HARole role) {
        controller = createMock(Controller.class);

        // Mock controller behavior
        reset(controller);
        IDebugCounterService counterService = new MockDebugCounterService();
        expect(controller.getDebugCounter()).andReturn(counterService).anyTimes();
        replay(controller);


        IShutdownService shutdownService = createMock(IShutdownService.class);
        roleManager = new RoleManager(controller, shutdownService , role, "test");

        // Make sure the desired role is set
        assertTrue(roleManager.getRole().equals(role));
    }

    @After
    public void tearDown() {
        verify(controller);
    }

    @Test
    public void testSetRoleStandbyToActive() throws Exception {
        doSetUp(HARole.STANDBY);

        this.setRoleAndMockController(HARole.ACTIVE);

        assertTrue(roleManager.getRole() == HARole.ACTIVE);

    }

    @Test
    public void testSetRoleActiveToStandby() throws Exception {
        // Set by default
        assertTrue(roleManager.getRole() == HARole.ACTIVE);

        this.setRoleAndMockController(HARole.STANDBY);

        assertTrue(roleManager.getRole() == HARole.STANDBY);

    }

    @Test
    public void testSetRoleActiveToActive() throws Exception {
        // Set by default
        assertTrue(roleManager.getRole() == HARole.ACTIVE);

        this.setRoleAndMockController(HARole.ACTIVE);

        assertTrue(roleManager.getRole() == HARole.ACTIVE);

    }

    @Test
    public void testSetRoleStandbyToStandby() throws Exception {
        doSetUp(HARole.STANDBY);

        this.setRoleAndMockController(HARole.STANDBY);

        assertTrue(roleManager.getRole() == HARole.STANDBY);

    }

    /**
     * Helper method that mocks up the controller and sets the supplied role
     * @param role the desired role to pass to setRole
     */
    private void setRoleAndMockController(HARole role) {
        reset(controller);
        controller.addUpdateToQueue(anyObject(IUpdate.class));
        expectLastCall().anyTimes();
        replay(controller);

        roleManager.setRole(role, "test");
    }
}