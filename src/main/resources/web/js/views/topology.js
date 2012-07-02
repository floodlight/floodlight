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
        this.hosts = this.options.hosts.models;
        this.host_links = [];
    },

    render:function (eventName) {
        $(this.el).html(this.template());
        var width = 900,
            height = 600;
        var color = d3.scale.category20();
        var force = d3.layout.force()
                             .charge(-500)
                             .linkDistance(200)
                             .size([width, height]);
        var svg = d3.select("#topology-graph").append("svg")
                    .attr("width", width)
                    .attr("height", height);
        if(this.model.nodes) {
            for (var i = 0; i < this.model.nodes.length; i++) {
                this.model.nodes[i].group = 1;
                this.model.nodes[i].id = this.model.nodes[i].name;
            }
            
            for (var i = 0; i < this.hosts.length; i++) {
                host = this.hosts[i];
                if (host.attributes['ipv4'].length > 0) {
                    host.name = host.attributes['ipv4'][0] + "\n" + host.id;
                } else {
                    host.name = host.id;
                }
                host.group = 2;
                //console.log(host);
            }
            
            var all_nodes = this.model.nodes.concat(this.hosts);
            
            var all_nodes_map = [];
            
            _.each(all_nodes, function(n) {
                all_nodes_map[n.id] = n;
            });
            
            for (var i = 0; i < this.hosts.length; i++) {
                host = this.hosts[i];
                //for (var j = 0; j < host.attributes['attachmentPoint'].length; j++) {
                for (var j = 0; j < 1; j++) { // FIXME hack to ignore multiple APs
                    var link = {source:all_nodes_map[host.id],
                                target:all_nodes_map[host.attributes['attachmentPoint'][j]['switchDPID']],
                                value:10};
                    //console.log(link);
                    if ( link.source && link.target) {
                        this.host_links.push(link);
                    } else {
                        console.log("Error: skipping link with undefined stuff!")
                    }
                }
            }
            
            var all_links = this.model.links.concat(this.host_links);
            
            force.nodes(all_nodes).links(all_links).start();
            var link = svg.selectAll("line.link").data(all_links).enter()
                          .append("line").attr("class", "link")
                          .style("stroke", function (d) { return "black"; });
            var node = svg.selectAll(".node").data(all_nodes)
                          .enter().append("g")
                          .attr("class", "node")
                          .call(force.drag);
                          
            node.append("image")
                .attr("xlink:href", function (d) {return d.group==1 ? "/ui/img/switch.png" : "/ui/img/server.png"})
                .attr("x", -16).attr("y", -16)
                .attr("width", 32).attr("height", 32);
            node.append("text").attr("dx", 20).attr("dy", ".35em")
                .text(function(d) { return d.name });
            node.on("click", function (d) {
                // TODO we could add some functionality here
                console.log('clicked '+d.name);
            });
            node.append("title").text(function(d) { return d.name; });
            force.on("tick", function() {
                link.attr("x1", function(d) { return d.source.x; })
                    .attr("y1", function(d) { return d.source.y; })
                    .attr("x2", function(d) { return d.target.x; })
                    .attr("y2", function(d) { return d.target.y; });
                node.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });
                
            });
        }
        return this;
    }
});
