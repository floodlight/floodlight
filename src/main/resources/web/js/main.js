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

var hackBase = ""; // put a URL here to access a different REST server

var AppRouter = Backbone.Router.extend({

    routes:{
        "":"home",
        "topology":"topology",
        "switches":"switchList",
        "switch/:id":"switchDetails",
        "switch/:id/port/:p":"portDetails", // not clear if needed
        "hosts":"hostList",
        "host/:id":"hostDetails",
        // "vlans":"vlanList" // maybe one day
        // "vlan/:id":"vlanDetails"
    },

    initialize:function () {
        this.headerView = new HeaderView();
        $('.header').html(this.headerView.render().el);

        // Close the search dropdown on click anywhere in the UI
        $('body').click(function () {
            $('.dropdown').removeClass("open");
        });
    },

    home:function () {
        $('#content').html(new HomeView().render().el);
        $('ul[class="nav"] > li').removeClass('active');
        $('a[href="/"]').parent().addClass('active');
    },

    topology:function () {
        //console.log("switching to topology view");
        var topo = new Topology();
        $('#content').html(new TopologyView({model:topo, hosts:hl}).render().el);
        // TODO factor this code out
        $('ul.nav > li').removeClass('active');
        $('li > a[href*="topology"]').parent().addClass('active');
    },
    
    switchDetails:function (id) {
        //console.log("switching [sic] to single switch view");
        var sw = swl.get(id);
        $('#content').html(new SwitchView({model:sw}).render().el);
        $('ul.nav > li').removeClass('active');
        $('li > a[href*="/switches"]').parent().addClass('active');
    },
    
    switchList:function () {
        //console.log("switching [sic] to switch list view");
        $('#content').html(new SwitchListView({model:swl}).render().el);
        $('ul.nav > li').removeClass('active');
        $('li > a[href*="/switches"]').parent().addClass('active');
    },

    hostDetails:function (id) {
        //console.log("switching to single host view");
        var h = hl.get(id);
        $('#content').html(new HostView({model:h}).render().el);
        $('ul.nav > li').removeClass('active');
        $('li > a[href*="/hosts"]').parent().addClass('active');
    },
    
    hostList:function () {
        //console.log("switching to host list view");
        $('#content').html(new HostListView({model:hl}).render().el);
        $('ul.nav > li').removeClass('active');
        $('li > a[href*="/hosts"]').parent().addClass('active');
    },

});

// load global models and reuse them
var swl = new SwitchCollection();
var hl  = new HostCollection();

var updating = true;

tpl.loadTemplates(['home', 'status', 'topology', 'header', 'switch', 'switch-list', 'switch-list-item', 'host', 'host-list', 'host-list-item', 'port-list', 'port-list-item', 'flow-list', 'flow-list-item'],
    function () {
        app = new AppRouter();
        Backbone.history.start({pushState: true});
        //console.log("started history")
        
        $(document).ready(function () {
            // trigger Backbone routing when clicking on links, thanks to Atinux and pbnv
            app.navigate("", true);

            window.document.addEventListener('click', function(e) {
                e = e || window.event
                var target = e.target || e.srcElement
                if ( target.nodeName.toLowerCase() === 'a' ) {
                    e.preventDefault()
                    var uri = target.getAttribute('href')
                    if (uri.substr(0,4) !== 'http') {
                        app.navigate(uri.substr(1), true)
                    }
                    else {
                        parent.window.location.assign(uri)
                    }
                }
            });
            window.addEventListener('popstate', function(e) {
                app.navigate(location.pathname.substr(1), true);
            });
            
            // wait for the page to be rendered before loading any data
            swl.fetch();
            hl.fetch();
            
            setInterval(function () {
                if(updating) {
                    swl.fetch();
                    hl.fetch();
                }
            }, 3000);
        });
    });
