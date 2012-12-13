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

window.Host = Backbone.Model.extend({

    defaults: {
        // vlan: -1,
        lastSeen: 'never',
        ip: ' ',
        swport: ' ',
    },

    // initialize:function () {}

});

window.HostCollection = Backbone.Collection.extend({

    model:Host,

    fetch:function () {
        var self = this;
        //console.log("fetching host list")
        $.ajax({
            url:hackBase + "/wm/device/",
            dataType:"json",
            success:function (data) {
                //console.log("fetched  host list: " + data.length);
                // console.log(data);
                // data is a list of device hashes
                var old_ids = self.pluck('id');
                //console.log("old_ids" + old_ids);
                _.each(data, function(h) {
                    h.id = h.mac[0];
                    old_ids = _.without(old_ids, h.id);
                    if (h['attachmentPoint'].length > 0) {
                        h.swport = _.reduce(h['attachmentPoint'], function(memo, ap) {
                            return memo + ap.switchDPID + "-" + ap.port + " "}, "");
                        //console.log(h.swport);
                        h.lastSeen = new Date(h.lastSeen).toLocaleString();
                        self.add(h, {silent: true});
                    }
                });
                // old_ids now holds hosts that no longer exist; remove them
                //console.log("old_ids" + old_ids);
                _.each(old_ids, function(h) {
                    console.log("---removing host " + h);
                    self.remove({id:h});
                });
                self.trigger('add'); // batch redraws
            }
        });

    },

    /*
     * findByName:function (key) { // TODO: Modify service to include firstName
     * in search var url = (key == '') ? '/host/' : "/host/search/" + key;
     * console.log('findByName: ' + key); var self = this; $.ajax({ url:url,
     * dataType:"json", success:function (data) { console.log("search success: " +
     * data.length); self.reset(data); } }); }
     */

});
