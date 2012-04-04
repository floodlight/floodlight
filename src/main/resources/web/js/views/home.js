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

window.HomeView = Backbone.View.extend({

    initialize:function () {
        // console.log('Initializing Home View');
        this.template = _.template(tpl.get('home'));
    },

    events:{
        "click #showMeBtn":"showMeBtnClick"
    },

    render:function (eventName) {
        $(this.el).html(this.template());
        var stats = new Status();
        $(this.el).find('#controller-status').html(new StatusView({model:stats}).render().el);
        $(this.el).find('#switch-list').html(new SwitchListView({model:swl}).render().el);
        $(this.el).find('#host-list').html(new HostListView({model:hl}).render().el);
        return this;
    },

    showMeBtnClick:function () {
        app.headerView.search();
    }

});