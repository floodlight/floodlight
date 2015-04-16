/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.core.web;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.restlet.data.Status;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.RoleInfo;
import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.annotations.LogMessageDoc;

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

public class ControllerRoleResource extends ServerResource {

    protected static Logger log = LoggerFactory.getLogger(ControllerRoleResource.class);
    
    private static final String STR_ACTIVE = "ACTIVE";
    private static final String STR_STANDBY = "STANDBY";
    private static final String STR_ROLE = "role";
    private static final String STR_ROLE_CHANGE_DESC = "role-change-description";
    private static final String STR_ROLE_CHANGE_DATE_TIME = "role-change-date-time";

    @Get("json")
    public Map<String, String> getRole() {
        IFloodlightProviderService floodlightProvider =
                (IFloodlightProviderService)getContext().getAttributes().
                    get(IFloodlightProviderService.class.getCanonicalName());
        Map<String, String> retValue = new HashMap<String, String>();
        RoleInfo ri = floodlightProvider.getRoleInfo();
        retValue.put(STR_ROLE, ri.getRole().toString());
        retValue.put(STR_ROLE_CHANGE_DESC, ri.getRoleChangeDescription());
        retValue.put(STR_ROLE_CHANGE_DATE_TIME, ri.getRoleChangeDateTime().toString());
        return retValue;
    }

    @Post
    @LogMessageDoc(level="WARN",
                   message="Invalid role value specified in REST API to " +
                      "set controller role",
                   explanation="An HA role change request was malformed.",
                   recommendation=LogMessageDoc.CHECK_CONTROLLER)
    public Map<String, String> setRole(String json) {
    	Map<String, String> retValue = new HashMap<String, String>();

        IFloodlightProviderService floodlightProvider =
                (IFloodlightProviderService)getContext().getAttributes().
                    get(IFloodlightProviderService.class.getCanonicalName());
        
		MappingJsonFactory f = new MappingJsonFactory();
		JsonParser jp = null;
		String role = null;
		String roleChangeDesc = null;
		
		retValue.put("TBD", "Not yet implemented");
		return retValue;
		/*
		try {
			try {
				jp = f.createJsonParser(json);
			} catch (IOException e) {
				e.printStackTrace();
			}


			jp.nextToken();
			if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
				throw new IOException("Expected START_OBJECT");
			}

			while (jp.nextToken() != JsonToken.END_OBJECT) {
				if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
					throw new IOException("Expected FIELD_NAME");
				}

				String n = jp.getCurrentName().toLowerCase();
				jp.nextToken();

				switch (n) {
				case STR_ROLE:
					role = jp.getText();
					break;
				case STR_ROLE_CHANGE_DESC:
					roleChangeDesc = jp.getText();
					break;
				default:
					retValue.put("ERROR", "Unrecognized JSON key.");
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			retValue.put("ERROR", "Caught IOException while parsing JSON POST request in role request.");
		}
    
        HARole harole = null;
        try {
        	harole = HARole.valueOfBackwardsCompatible(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            // The role value in the REST call didn't match a valid
            // role name, so just leave the role as null and handle
            // the error below.
        }

        if (roleChangeDesc == null) {
            roleChangeDesc = "<none>";
        }

        floodlightProvider.setRole(harole, roleChangeDesc);
        
        RoleInfo ri = floodlightProvider.getRoleInfo();
        retValue.put(STR_ROLE, ri.getRole().toString());
        retValue.put(STR_ROLE_CHANGE_DESC, ri.getRoleChangeDescription());
        retValue.put(STR_ROLE_CHANGE_DATE_TIME, ri.getRoleChangeDateTime().toString());
        
		return retValue;*/
    }
}
