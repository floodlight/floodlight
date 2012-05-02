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
        // code from D3 force-directed graph example since there's no other docs
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
          force.nodes(this.model.nodes).links(this.model.links).start();
          var link = svg.selectAll("line.link").data(this.model.links).enter()
                    .append("line").attr("class", "link")
                    .style("stroke", function (d) { return "black"; });
          var node = svg.selectAll("circle.node").data(this.model.nodes)
                        .enter().append("circle")
                        .attr("class", "node")
                        .attr("r", 10)
                        .style("fill", function(d) { return color(d.group); })
                        .call(force.drag);
          node.append("title").text(function(d) { return d.name; });
          force.on("tick", function() {
            link.attr("x1", function(d) { return d.source.x; })
                .attr("y1", function(d) { return d.source.y; })
                .attr("x2", function(d) { return d.target.x; })
                .attr("y2", function(d) { return d.target.y; });

            node.attr("cx", function(d) { return d.x; })
                .attr("cy", function(d) { return d.y; });
          });
        }
        return this;
    }
});
