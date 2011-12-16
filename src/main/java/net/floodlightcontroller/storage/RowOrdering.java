/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.storage;

import java.util.ArrayList;
import java.util.List;

public class RowOrdering {
    
    public enum Direction { ASCENDING, DESCENDING };
    
    public class Item {
        
        private String column;
        private Direction direction;
        
        public Item(String column, Direction direction) {
            assert(column != null);
            assert(direction != null);
            this.column = column;
            this.direction = direction;
        }
        
        public String getColumn() {
            return column;
        }
        
        public Direction getDirection() {
            return direction;
        }
    }
    
    private List<Item> itemList = new ArrayList<Item>();
    
    public RowOrdering() {
    }
    
    public RowOrdering(String column) {
        add(column);
    }
    
    public RowOrdering(String column, Direction direction) {
        add(column, direction);
    }
    
    public RowOrdering(Item item) {
        add(item);
    }
    
    public RowOrdering(Item[] itemArray) {
        add(itemArray);
    }
    
    public RowOrdering(List<Item> itemList) {
        add(itemList);
    }
    
    public void add(String column) {
        itemList.add(new Item(column, Direction.ASCENDING));
    }
    
    public void add(String column, Direction direction) {
        itemList.add(new Item(column, direction));
    }
    
    public void add(Item item) {
        assert(item != null);
        itemList.add(item);
    }
    
    public void add(Item[] itemArray) {
        for (Item item: itemArray) {
            itemList.add(item);
        }
    }
    
    public void add(List<Item> itemList) {
        this.itemList.addAll(itemList);
    }
    
    public List<Item> getItemList() {
        return itemList;
    }
    
    public boolean equals(RowOrdering rowOrdering) {
        if (rowOrdering == null)
            return false;
        
        int len1 = itemList.size();
        int len2 = rowOrdering.getItemList().size();
        if (len1 != len2)
            return false;
        
        for (int i = 0; i < len1; i++) {
            Item item1 = itemList.get(i);
            Item item2 = rowOrdering.getItemList().get(i);
            if (!item1.getColumn().equals(item2.getColumn()) ||
                    item1.getDirection() != item2.getDirection())
                return false;
        }
        
        return true;
    }
}
