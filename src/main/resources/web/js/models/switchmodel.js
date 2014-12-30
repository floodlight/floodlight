
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
                                      version: '',
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
                                             //console.log(data['desc']);
                                             self.set(data['desc']);
                                             }
                                             });
                                      
                                      //console.log("fetching switch " + this.id + " aggregate")
                                      $.ajax({
                                             url:hackBase + "/wm/core/switch/" + self.id + '/aggregate/json',
                                             dataType:"json",
                                             success:function (data) {
                                             //console.log("fetched  switch " + self.id + " aggregate");
                                             //console.log(data['aggregate']);
                                             self.set(data['aggregate']);
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
                                                    var old_ids = self.ports.pluck('id');
                                                    //console.log("old_ids" + old_ids);
                                                    
                                                    // create port models
                                                    _.each(data['port'], function(p) {
                                                           // workaround for REST serialization signed/unsigned bug
                                                           if(p.portNumber < 0) {p.portNumber = 65536 + p.portNumber};
                                                           
                                                           p.id = self.id+'-'+p.portNumber;
                                                           old_ids = _.without(old_ids, p.id);
                                                           p.dropped = p.receiveDropped + p.transmitDropped;
                                                           p.errors = p.receiveCRCErrors + p.receiveFrameErrors + p.receiveOverrunErrors +
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
                                                    url:hackBase + "/wm/core/switch/" + self.id + '/port-desc/json',
                                                    dataType:"json",
                                                    success:function (data) {
                                                    //console.log("fetched  switch " + self.id + " features");
                                                    //console.log(data['portDesc']);
                                                    // update port models
                                                    _.each(data['portDesc'], function(p) {
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
                                             var flows = data['flows'];
                                             //console.log(flows);
                                             
                                             // create flow models
                                             var i = 0;
                                             _.each(flows, function(f) {
                                                    f.id = self.id + '-' + i++;
                                                    
                                                    // build human-readable match
                                                    f.matchHTML = '';
                                                    if(f.hasOwnProperty('match')) {
                                                    _.each(f.match, function(value , key) {
                                                           f.matchHTML += key + "=" + value +" ";
                                                           },f);
                                                    } else {
                                                        f.matchHTML = "---";
                                                    }
                                                    f.matchHTML = f.matchHTML.substr(0, f.matchHTML.length - 1);
                                                    
                                                    f.applyActionText = '';
                                                    f.writeActionText = '';
                                                    f.clearActionText = '';
                                                    f.writeMetadataText = '';
                                                    f.gotoMeterText = '';
                                                    f.gotoGroupText = '';
                                                    f.experimenterText = '';
                                                    if(f.hasOwnProperty('instructions')) {
                                                    if(f.instructions.hasOwnProperty('instruction_apply_actions')) {
                                                    _.each(f.instructions.instruction_apply_actions, function(value, key) {
                                                           f.applyActionText += key + ":" + value + " ";
                                                           },f);
                                                    } else {
                                                        f.applyActionText = "----";
                                                    }
                                                    if(f.instructions.hasOwnProperty('instruction_write_actions')) {
                                                    _.each(f.instructions.instruction_write_actions, function(value, key) {
                                                           f.writeActionText += key + ":" + value + " ";
                                                           },f);
                                                    } else {
                                                        f.writeActionText = "----";
                                                    }
                                                    if(f.instructions.hasOwnProperty('instruction_clear_actions')) {
                                                           f.clearActionText = "clear";
                                                    } else {
                                                        f.clearActionText = "---";
                                                    }
                                                    if(f.instructions.hasOwnProperty('instruction_write_metadata')) {
                                                            f.writeMetadataText = f.instructions.instruction_write_metadata;
                                                    } else {
                                                        f.writeMetadataText = "---";
                                                    }
                                                    if(f.instructions.hasOwnProperty('instruction_goto_group')) {
                                                            f.gotoGroupText = f.instructions.instruction_goto_group;
                                                    } else {
                                                        f.gotoGroupText = "---";
                                                    }
                                                    if(f.instructions.hasOwnProperty('instruction_goto_meter')) {
                                                            f.gotoMeterText = f.instructions.instruction_goto_meter;
                                                    } else {
                                                        f.gotoMeterText = "---";
                                                    }
                                                    if(f.instructions.hasOwnProperty('instruction_experimenter')) {
                                                        f.experimenterText = f.instructions.instruction_experimenter;
                                                    } else {
                                                        f.experimenterText = "---";
                                                    }
                                                    
                                                    } else if(f.hasOwnProperty('actions')) { // OF1.0's actions will go under "apply actions"
                                                    _.each(f.actions, function(value, key) {
                                                           f.applyActionText += key + ":" + value + " ";
                                                           },f);
                                                        f.writeActionText = "n/a "; // need extra space at end
                                                        f.clearActionText = "n/a";
                                                        f.writeMetadataText = "n/a";
                                                        f.gotoGroupText = "n/a";
                                                        f.gotoMeterText = "n/a";
                                                        f.experimenterText = "n/a";
                                                    } else {
                                                        f.applyActionText = "----";
                                                        f.writeActionText = "n/a "; // need extra space at end
                                                        f.clearActionText = "n/a";
                                                        f.writeMetadataText = "n/a";
                                                        f.gotoGroupText = "n/a";
                                                        f.gotoMeterText = "n/a";
                                                        f.experimenterText = "n/a";
                                                    }
                                                    
                                                    // build human-readable instrucions
                                                    f.applyActionText = f.applyActionText.substr(0, f.applyActionText.length - 1);
                                                    f.writeActionText = f.writeActionText.substr(0, f.writeActionText.length - 1);
   
                                                    // table
                                                    f.tableText = '';
                                                    if(f.hasOwnProperty('tableId')) {
                                                           f.tableText = f.tableId;
                                                    } else {
                                                        f.applyActionText = "---";
                                                    }
                                                    
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
                                                                   old_ids = _.without(old_ids, sw['switchDPID']);
                                                                   self.add({id: sw['switchDPID'], inetAddress: sw.inetAddress,
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