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

window.SwitchView = Backbone.View.extend({

    initialize:function () {
        this.template = _.template(tpl.get('switch'));
        this.model.bind("change", this.render, this);
        //this.model.bind("destroy", this.close, this);
        
        // some parts of the model are large and are only needed in detail view
        this.model.loadPorts();
        this.model.loadFlows();
    },

    render:function (eventName) {
        $(this.el).html(this.template(this.model.toJSON()));
        $(this.el).find('#port-list').html(new PortListView({model:this.model.ports}).render().el);
        $(this.el).find('#flow-list').html(new FlowListView({model:this.model.flows}).render().el);
        return this;
    }

});

window.SwitchListItemView = Backbone.View.extend({

    tagName:"tr",

    initialize:function () {
        this.template = _.template(tpl.get('switch-list-item'));
        this.model.bind("change", this.render, this);
    //this.model.bind("destroy", this.close, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template(this.model.toJSON()));
        return this;
    }

});

window.SwitchListView = Backbone.View.extend({

    initialize:function () {
        this.template = _.template(tpl.get('switch-list'));
        this.model.bind("change", this.render, this);
        this.model.bind("remove", this.render, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template({nswitches:swl.length}));
        _.each(this.model.models, function (sw) {
            $(this.el).find('table.switch-table > tbody')
                .append(new SwitchListItemView({model:sw}).render().el);
        }, this);
        return this;
    },

});

