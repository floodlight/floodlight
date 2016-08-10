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
                // create port models
                _.each(data[self.id], function(p) {
                    p.id = self.id+'-'+p.portNumber;
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
                // console.log(data[self.id]);
                // create flow models
                var i = 0;
                _.each(data[self.id], function(f) {
                    f.id = self.id + '-' + i++;
                    f.matchHTML = "src=<a href='/host/" + f.match.dataLayerSource + "'>" +
                        f.match.dataLayerSource +
                        "</a>, dst=<a href='/host/" + f.match.dataLayerDestination + "'>" +
                        f.match.dataLayerDestination + 
                        "</a>, port=" + f.match.inputPort; // FIXME
                    f.actionText = f.actions[0].type + " " + f.actions[0].port; // FIXME
                    // console.log(f);
                    self.flows.add(f, {silent: true});
                });
                self.flows.trigger('add');
            }
        });
    },
});

window.SwitchCollection = Backbone.Collection.extend({

    model:Switch,
    
    initialize:function () {
        var self = this;
        //console.log("fetching switch list")
        $.ajax({
            url:hackBase + "/wm/core/controller/switches/json",
            dataType:"json",
            success:function (data) {
                //console.log("fetched  switch list: " + data.length);
                // console.log(data);
                _.each(data, function(sw) {self.add({id: sw['dpid']})});
            }
        });
    },

  fetch:function () {
	this.initialize()
    }


});
