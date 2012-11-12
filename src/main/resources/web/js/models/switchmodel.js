/*
   Copyright 2012 IBM

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

window.Switch = Backbone.Model.extend({

    urlRoot:"/wm/core/switch/",
    
    defaults: {
        datapathDescription: '',
        hardwareDescription: '',
        manufacturerDescription: '',
        serialNumber: '',
        softwareDescription: '',
        flowCount: ' ',
        packetCount: ' ',
        byteCount: ' ',
    },

    initialize:function () {
        var self = this;

        //console.log("fetching switch " + this.id + " desc")
        $.ajax({
            url:hackBase + "/wm/core/switch/" + self.id + '/desc/json',
            dataType:"json",
            success:function (data) {
                //console.log("fetched  switch " + self.id + " desc");
                //console.log(data[self.id][0]);
                self.set(data[self.id][0]);
            }
        });

        //console.log("fetching switch " + this.id + " aggregate")
        $.ajax({
            url:hackBase + "/wm/core/switch/" + self.id + '/aggregate/json',
            dataType:"json",
            success:function (data) {
                //console.log("fetched  switch " + self.id + " aggregate");
                //console.log(data[self.id][0]);
                self.set(data[self.id][0]);
            }
        });
        self.trigger('add');
        this.ports = new PortCollection();
        this.flows = new FlowCollection();
        //this.loadPorts();
        //this.loadFlows();
    },

    fetch:function () {
        this.initialize()
    },

    loadPorts:function () {
        var self = this;
        //console.log("fetching switch " + this.id + " ports")
        //console.log("fetching switch " + this.id + " features")
        $.when($.ajax({
            url:hackBase + "/wm/core/switch/" + self.id + '/port/json',
            dataType:"json",
            success:function (data) {
                //console.log("fetched  switch " + self.id + " ports");
                //console.log(data[self.id]);
                var old_ids = self.ports.pluck('id');
                //console.log("old_ids" + old_ids);

                // create port models
                _.each(data[self.id], function(p) {
                    // workaround for REST serialization signed/unsigned bug
                    if(p.portNumber < 0) {p.portNumber = 65536 + p.portNumber};
                    
                    p.id = self.id+'-'+p.portNumber;
                    old_ids = _.without(old_ids, p.id);
                    p.dropped = p.receiveDropped + p.transmitDropped;
                    p.errors = p.receiveCRCErrors + p.receiveErrors + p.receiveOverrunErrors +
                        p.receiveFrameErrors + p.transmitErrors;
                    // this is a knda kludgy way to merge models
                    var m = self.ports.get(p.id);
                    if(m) {
                        m.set(p, {silent: true});
                    } else {
                        self.ports.add(p, {silent: true});
                    }
                    //console.log(p);
                });
                
                // old_ids now holds ports that no longer exist; remove them
                //console.log("old_ids" + old_ids);
                _.each(old_ids, function(p) {
                    console.log("removing port " + p);
                    self.remove({id:p});
                });
            }
        }),
        $.ajax({
            url:hackBase + "/wm/core/switch/" + self.id + '/features/json',
            dataType:"json",
            success:function (data) {
                //console.log("fetched  switch " + self.id + " features");
                //console.log(data[self.id]);
                // update port models
                _.each(data[self.id].ports, function(p) {
                    p.id = self.id+'-'+p.portNumber;
                    if(p.name != p.portNumber) {
                        p.name = p.portNumber + ' (' + p.name + ')';
                    }
                    p.status = '';
                    p.status += (p.state & 1) ? 'DOWN' : 'UP';
                    switch(p.currentFeatures & 0x7f) {
                    case 1:
                        p.status += ' 10 Mbps';
                        break;
                    case 2:
                        p.status += ' 10 Mbps FDX';
                        break;
                    case 4:
                        p.status += ' 100 Mbps';
                        break;
                    case 8:
                        p.status += ' 100 Mbps FDX';
                        break;
                    case 16:
                        p.status += ' 1 Gbps'; // RLY?
                        break;
                    case 32:
                        p.status += ' 1 Gbps FDX';
                        break;
                    case 64:
                        p.status += ' 10 Gbps FDX';
                        break;
                    }
                    // TODO parse copper/fiber, autoneg, pause
                    
                    // this is a knda kludgy way to merge models
                    var m = self.ports.get(p.id);
                    if(m) {
                        m.set(p, {silent: true});
                    } else {
                        self.ports.add(p, {silent: true});
                    }
                    //console.log(p);
                });
            }
        })).done(function() {
            self.ports.trigger('add'); // batch redraws
        });
    },
    
    loadFlows:function () {
        var self = this;
        //console.log("fetching switch " + this.id + " flows")
        $.ajax({
            url:hackBase + "/wm/core/switch/" + self.id + '/flow/json',
            dataType:"json",
            success:function (data) {
                //console.log("fetched  switch " + self.id + " flows");
                var flows = data[self.id];
                //console.log(flows);

                // create flow models
                var i = 0;
                _.each(flows, function(f) {
                    f.id = self.id + '-' + i++;

                    // build human-readable match
                    f.matchHTML = '';
                    if(!(f.match.wildcards & (1<<0))) { // input port
                        f.matchHTML += "port=" + f.match.inputPort + ", ";
                    }
                    if(!(f.match.wildcards & (1<<1))) { // VLAN ID
                        f.matchHTML += "VLAN=" + f.match.dataLayerVirtualLan + ", ";
                    }
                    if(!(f.match.wildcards & (1<<20))) { // VLAN prio
                        f.matchHTML += "prio=" + f.match.dataLayerVirtualLanPriorityCodePoint  + ", ";
                    }
                    if(!(f.match.wildcards & (1<<2))) { // src MAC
                        f.matchHTML += "src=<a href='/host/" + f.match.dataLayerSource + "'>" +
                        f.match.dataLayerSource + "</a>, ";
                    }
                    if(!(f.match.wildcards & (1<<3))) { // dest MAC
                        f.matchHTML += "dest=<a href='/host/" + f.match.dataLayerDestination + "'>" +
                        f.match.dataLayerDestination + "</a>, ";
                    }
                    if(!(f.match.wildcards & (1<<4))) { // Ethertype
                        // TODO print a human-readable name instead of hex
                        f.matchHTML += "ethertype=" + f.match.dataLayerType + ", ";
                    }
                    if(!(f.match.wildcards & (1<<5))) { // IP protocol
                        // TODO print a human-readable name
                        f.matchHTML += "proto=" + f.match.networkProtocol + ", ";
                    }
                    if(!(f.match.wildcards & (1<<6))) { // TCP/UDP source port
                        f.matchHTML += "IP src port=" + f.match.transportSource + ", ";
                    }
                    if(!(f.match.wildcards & (1<<7))) { // TCP/UDP dest port
                        f.matchHTML += "IP dest port=" + f.match.transportDestination  + ", ";
                    }
                    if(!(f.match.wildcards & (32<<8))) { // src IP
                        f.matchHTML += "src=" + f.match.networkSource  + ", ";
                    }
                    if(!(f.match.wildcards & (32<<14))) { // dest IP
                        f.matchHTML += "dest=" + f.match.networkDestination  + ", ";
                    }
                    if(!(f.match.wildcards & (1<<21))) { // IP TOS
                        f.matchHTML += "TOS=" + f.match.networkTypeOfService  + ", ";
                    }
                    // remove trailing ", "
                    f.matchHTML = f.matchHTML.substr(0, f.matchHTML.length - 2);

                    // build human-readable action list
                    f.actionText = _.reduce(f.actions, function (memo, a) {
                        switch (a.type) {
                            case "OUTPUT":
                                return memo + "output " + a.port + ', ';
                            case "OPAQUE_ENQUEUE":
                                return memo + "enqueue " + a.port + ':' + a.queueId +  ', ';
                            case "STRIP_VLAN":
                                return memo + "strip VLAN, ";
                            case "SET_VLAN_ID":
                                return memo + "VLAN=" + a.virtualLanIdentifier + ', ';
                            case "SET_VLAN_PCP":
                                return memo + "prio=" + a.virtualLanPriorityCodePoint + ', ';
                            case "SET_DL_SRC":
                                return memo + "src=" + a.dataLayerAddress + ', ';
                            case "SET_DL_DST":
                                return memo + "dest=" + a.dataLayerAddress + ', ';
                            case "SET_NW_TOS":
                                return memo + "TOS=" + a.networkTypeOfService + ', ';
                            case "SET_NW_SRC":
                                return memo + "src=" + a.networkAddress + ', ';
                            case "SET_NW_DST":
                                return memo + "dest=" + a.networkAddress + ', ';
                            case "SET_TP_SRC":
                                return memo + "src port=" + a.transportPort + ', ';
                            case "SET_TP_DST":
                                return memo + "dest port=" + a.transportPort + ', ';
                        }
                    }, "");
                    // remove trailing ", "
                    f.actionText = f.actionText.substr(0, f.actionText.length - 2);

                    //console.log(f);
                    self.flows.add(f, {silent: true});
                });
                self.flows.trigger('add');
            }
        });
    },
});

window.SwitchCollection = Backbone.Collection.extend({

    model:Switch,
    
    fetch:function () {
        var self = this;
        //console.log("fetching switch list")
        $.ajax({
            url:hackBase + "/wm/core/controller/switches/json",
            dataType:"json",
            success:function (data) {
                //console.log("fetched  switch list: " + data.length);
                //console.log(data);
                var old_ids = self.pluck('id');
                //console.log("old_ids" + old_ids);
                
                _.each(data, function(sw) {
                    old_ids = _.without(old_ids, sw['dpid']);
                    self.add({id: sw['dpid'], inetAddress: sw.inetAddress,
                              connectedSince: new Date(sw.connectedSince).toLocaleString()})});
                
                // old_ids now holds switches that no longer exist; remove them
                //console.log("old_ids" + old_ids);
                _.each(old_ids, function(sw) {
                    console.log("removing switch " + sw);
                    self.remove({id:sw});
                });
            },
        });
    },

});
