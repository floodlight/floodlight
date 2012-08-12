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

// not used for now
window.FlowView = Backbone.View.extend({

    initialize:function () {
        this.template = _.template(tpl.get('flow'));
        this.model.bind("change", this.render, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template(this.model.toJSON()));
        return this;
    }

});

window.FlowListItemView = Backbone.View.extend({

    tagName:"tr",

    initialize:function () {
        this.template = _.template(tpl.get('flow-list-item'));
        this.model.bind("change", this.render, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template(this.model.toJSON()));
        return this;
    }

});

// TODO throughput (bps) and pps sparklines would be nice here
// TODO hovering over a MAC address could show a compact view of that host
window.FlowListView = Backbone.View.extend({

    initialize:function () {
        this.template = _.template(tpl.get('flow-list'));
        this.model.bind("change", this.render, this);
        this.model.bind("add", this.render, this);
    },

    render:function (eventName) {
        // console.log("rendering flow list view: " + this.model.models.length);
        $(this.el).html(this.template({nflows:this.model.length}));
        _.each(this.model.models, function (f) {
            $(this.el).find('table.flow-table > tbody')
                .append(new FlowListItemView({model:f}).render().el);
        }, this);
        return this;
    },

});

