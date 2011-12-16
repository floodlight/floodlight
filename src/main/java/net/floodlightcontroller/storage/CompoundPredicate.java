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

/** Predicate class to handle AND and OR combinations of a number
 * of child predicates. The result of the logical combination of the
 * child predicates can also be negated to support a NOT operation.
 * 
 * @author rob
 *
 */
public class CompoundPredicate implements IPredicate {

    public enum Operator { AND, OR };
    
    private Operator operator;
    private boolean negated;
    private IPredicate[] predicateList;
    
    public CompoundPredicate(Operator operator, boolean negated, IPredicate... predicateList) {
        this.operator = operator;
        this.negated = negated;
        this.predicateList = predicateList;
    }
    
    public Operator getOperator() {
        return operator;
    }
    
    public boolean isNegated() {
        return negated;
    }
    
    public IPredicate[] getPredicateList() {
        return predicateList;
    }
}
