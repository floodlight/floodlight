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

package net.floodlightcontroller.storage.sql;

import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.storage.IPredicate;
import net.floodlightcontroller.storage.IQuery;
import net.floodlightcontroller.storage.RowOrdering;

public class SqlQuery implements IQuery {

	private String tableName;
	private String[] columnNameList;
	private IPredicate predicate;
	private RowOrdering rowOrdering;
	private Map<String, Comparable<?>> parameterMap;

	SqlQuery(String className, String[] columnNameList, IPredicate predicate,
			RowOrdering rowOrdering) {
		this.tableName = className;
		this.columnNameList = columnNameList;
		this.predicate = predicate;
		this.rowOrdering = rowOrdering;
	}

	@Override
	public void setParameter(String name, Object value) {
		if (parameterMap == null)
			parameterMap = new HashMap<String, Comparable<?>>();
		parameterMap.put(name, (Comparable<?>) value);
	}

	public String getTableName() {
		return tableName;
	}

	public String[] getColumnNameList() {
		return columnNameList;
	}

	public IPredicate getPredicate() {
		return predicate;
	}

	public RowOrdering getRowOrdering() {
		return rowOrdering;
	}

	public Comparable<?> getParameter(String name) {
		Comparable<?> value = null;
		if (parameterMap != null) {
			value = parameterMap.get(name);
		}
		return value;
	}

	public Map<String, Comparable<?>> getParameterMap() {
		return parameterMap;
	}
}
