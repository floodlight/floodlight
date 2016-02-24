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

window.HeaderView = Backbone.View.extend({

    initialize:function () {
        this.template = _.template(tpl.get('header'));
        // this.searchResults = new HostCollection();
        // this.searchresultsView = new SearchListView({model:this.searchResults, className:'dropdown-menu'});
    },

    render:function (eventName) {
        $(this.el).html(this.template());
        $('#live-updates', this.el).change(function () {
            updating = $(this).is(':checked');
        })
        // $('.navbar-search', this.el).append(this.searchresultsView.render().el);
        return this;
    },

    events:{
        "keyup .search-query":"search"
    },

    search:function (event) {
//        var key = event.target.value;
        var key = $('#searchText').val();
        console.log('search ' + key);
        // TODO search the host and switch lists
        this.searchResults.findByName(key);
        setTimeout(function () {
            $('#searchForm').addClass('open');
        });
    }

});