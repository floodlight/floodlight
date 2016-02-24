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
window.PortView = Backbone.View.extend({

    initialize:function () {
        this.template = _.template(tpl.get('port'));
        this.model.bind("change", this.render, this);
        //this.model.bind("destroy", this.close, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template(this.model.toJSON()));
        return this;
    }

});

window.PortListItemView = Backbone.View.extend({

    tagName:"tr",

    initialize:function () {
        this.template = _.template(tpl.get('port-list-item'));
        this.model.bind("change", this.render, this);
        //this.model.bind("destroy", this.close, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template(this.model.toJSON()));
        return this;
    }

});

// TODO throughput sparklines would be nice here
window.PortListView = Backbone.View.extend({

    initialize:function () {
        this.template = _.template(tpl.get('port-list'));
        this.model.bind("change", this.render, this);
        this.model.bind("add", this.render, this);
    },

    render:function (eventName) {
        // console.log("rendering port list view");
        $(this.el).html(this.template({nports:this.model.length}));
        _.each(this.model.models, function (p) {
            $(this.el).find('table.port-table > tbody')
                .append(new PortListItemView({model:p}).render().el);
        }, this);
        return this;
    },

});

