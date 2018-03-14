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

package net.floodlightcontroller.perfmon;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

import java.io.IOException;

public class PerfMonToggleResource extends ServerResource {

    @Get
    public Object getConfig() {
        IPktInProcessingTimeService pktInProcessingTimeService = (IPktInProcessingTimeService) getContext()
                .getAttributes().get(IPktInProcessingTimeService.class.getCanonicalName());

        return ImmutableMap.of("enabled", pktInProcessingTimeService.isEnabled());

    }

    @Put
    @Post
    public Object configure() throws IOException {
        IPktInProcessingTimeService pktInProcessingTimeService = (IPktInProcessingTimeService) getContext()
                .getAttributes().get(IPktInProcessingTimeService.class.getCanonicalName());

        if (getRequestAttributes().get("perfmonstate") == null) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Not a valid request.");
            return null;
        }

        String param = ((String) getRequestAttributes().get("perfmonstate")).toLowerCase();

        if (param.equals("reset")) {
            if (!pktInProcessingTimeService.isEnabled()) {
                pktInProcessingTimeService.setEnabled(true);
            }
            pktInProcessingTimeService.getCtb().reset();
        }
        else if (param.equals("enable")) {
            pktInProcessingTimeService.setEnabled(true);
        }
        else if (param.equals("disable")) {
            pktInProcessingTimeService.setEnabled(false);
        }
        else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Not a valid request.");
            return null;
        }

        setStatus(Status.SUCCESS_OK, "OK");
        return "{ \"enabled\" : " + pktInProcessingTimeService.isEnabled() + " }";

    }


}
