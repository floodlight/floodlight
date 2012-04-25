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
    	vlan: -1,
    	seen: 'never',
    	ip: ' ',
    	swport: ' ',
    },

    // initialize:function () {}

});

window.HostCollection = Backbone.Collection.extend({

    model:Host,

    initialize:function () {
        var self = this;
        console.log("fetching host list")
        $.ajax({
            url:hackBase + "/wm/devicemanager/device/all/json",
            dataType:"json",
            success:function (data) {
                console.log("fetched  host list: " + _.keys(data).length);
                // console.log(data);
                // data is a hash where each key is a MAC address
                _.each(_.keys(data), function(h) {
                	var d = data[h];
                	if (d['attachment-points'].length > 0) {
                		d.id = h;
                		d.seen = d['last-seen'];
                		d.swport = _.reduce(d['attachment-points'], function(memo, ap) {
                			return memo + ap.switch + "-" + ap.port + " "}, "")
                		d.ip = _.reduce(d['network-addresses'], function(memo, na) {return memo + na.ip + " "}, "")
                		// console.log(d);
                		self.add(d, {silent: true});
                	}
                });
                self.trigger('add'); // batch redraws
            }
        });

    }

    /*
    findByName:function (key) {
        // TODO: Modify service to include firstName in search
        var url = (key == '') ? '/host/' : "/host/search/" + key;
        console.log('findByName: ' + key);
        var self = this;
        $.ajax({
            url:url,
            dataType:"json",
            success:function (data) {
                console.log("search success: " + data.length);
                self.reset(data);
            }
        });
    }
    */

});