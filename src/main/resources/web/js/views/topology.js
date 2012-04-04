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

window.TopologyView = Backbone.View.extend({
    initialize:function () {
        this.template = _.template(tpl.get('topology'));
        this.model.bind("change", this.render, this);
    },

    render:function (eventName) {
        $(this.el).html(this.template());
        var width = 940,
        	height = 940; // might as well make it square
        var color = d3.scale.category20();
        var force = d3.layout.force()
            .charge(-120)
            .linkDistance(30)
            .size([width, height]);
        var svg = d3.select("#topology-graph").append("svg")
            .attr("width", width)
            .attr("height", height);
        if(this.model.nodes) {
        	// FIXME no clue why this doesn't work
        	force.nodes(this.model.nodes).links(this.model.links).start();
        }
        return this;
    }
});