/**
*    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
*    University
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

package org.openflow.protocol;

public enum OFPort {
    OFPP_MAX                ((short)0xff00),
    OFPP_IN_PORT            ((short)0xfff8),
    OFPP_TABLE              ((short)0xfff9),
    OFPP_NORMAL             ((short)0xfffa),
    OFPP_FLOOD              ((short)0xfffb),
    OFPP_ALL                ((short)0xfffc),
    OFPP_CONTROLLER         ((short)0xfffd),
    OFPP_LOCAL              ((short)0xfffe),
    OFPP_NONE               ((short)0xffff);

    protected short value;

    private OFPort(short value) {
        this.value = value;
    }

    /**
     * @return the value
     */
    public short getValue() {
        return value;
    }
}
