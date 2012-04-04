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

window.Status = Backbone.Model.extend({

    // url:"http://localhost:8080/wm/core/memory/json",
    
    defaults: {
    	host: 'localhost',
    	ofport: 6633,
    	uptime: 'unknown',
    	free: 0,
    	total: 0,
    	healthy: 'unknown',
    	modules: []
    },
    
    initialize:function () {
        var self = this;
        console.log("fetching controller status");
        $.ajax({
            url:hackBase + "/wm/core/health/json",
            dataType:"json",
            success:function (data) {
                console.log("fetched controller status: health");
                self.set(data);
                // console.log(self.toJSON());
            }
        });
        $.ajax({
            url:hackBase + "/wm/core/memory/json",
            dataType:"json",
            success:function (data) {
                console.log("fetched controller status: memory");
                self.set(data);
                // console.log(self.toJSON());
            }
        });
        $.ajax({
            url:hackBase + "/wm/core/module/loaded/json",
            dataType:"json",
            success:function (data) {
                console.log("fetched controller status: modules loaded");
                // console.log(data);
                // TODO format this better
                self.set({modules:_.keys(data)});
            }
        });

    }

});