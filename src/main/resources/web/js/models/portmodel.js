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

window.Port = Backbone.Model.extend({

    defaults: {
        name: '',
        receiveBytes: 0,
        receivePackets: 0,
        transmitBytes: 0,
        transmitPackets: 0,
        dropped: 0,
        errors: 0,
    },

    initialize:function () {
        // TODO hook up associated hosts
    }

});

window.PortCollection = Backbone.Collection.extend({

    model:Port,
    
    // instead of the collection loading its children, the switch will load them
    initialize:function () {}
    
});