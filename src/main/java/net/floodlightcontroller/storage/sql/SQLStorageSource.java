package net.floodlightcontroller.storage.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.storage.AbstractStorageSource;
import net.floodlightcontroller.storage.CompoundPredicate;
import net.floodlightcontroller.storage.IPredicate;
import net.floodlightcontroller.storage.IQuery;
import net.floodlightcontroller.storage.IResultSet;
import net.floodlightcontroller.storage.OperatorPredicate;
import net.floodlightcontroller.storage.RowOrdering;
import net.floodlightcontroller.storage.StorageSourceNotification;
import net.floodlightcontroller.storage.RowOrdering.Item;
import net.floodlightcontroller.storage.StorageSourceNotification.Action;

public class SQLStorageSource extends AbstractStorageSource {
	
	public Connection openConnection() {
		Connection conn = null;
		return conn;
	}
	
	/*
	 * Create an IQuery Object. (Select <columnNames> from <tableName> where <predicate> order by <ordering>)
	 * @param tableName
	 * @param columnNames
	 * @param predicate
	 * @param ordering
	 */
	@Override
	public IQuery createQuery(String tableName, String[] columnNames,
			IPredicate predicate, RowOrdering ordering) {
		IQuery query = new SqlQuery(tableName, columnNames, predicate, ordering);

		return query;
	}

	/*
	 * create a table, all columns will be of type VARCHAR(100) for simplicity's sake. If no columns are initially
	 * passed to the method, nothing is done and the table will be created later.
	 * @param tableName
	 * @param indexedColumns
	 */
	@Override
	public void createTable(String tableName, Set<String> indexedColumns) {
		StringBuffer sb = new StringBuffer();
		try {
			if (indexedColumns != null){
				Connection conn = openConnection();
	
				Statement stmt = conn.createStatement();
	
				sb.append("CREATE TABLE if not exists " + tableName + "(");
				Iterator<String> it = indexedColumns.iterator();
				for (int i = 0; i < indexedColumns.size() - 1; i++) {
					sb.append(it.next().toString() + " varchar(100), ");
				}
				sb.append(it.next().toString() + " varchar(100));");
				
				//System.out.println(sb.toString());
				stmt.execute(sb.toString());
	
				conn.close();
			}
		} catch (SQLException e) {
			System.out.println("could not create the table\n\t"
					+ e.getMessage() + "\n\t" + sb.toString());
		}

	}
	
	/*
	 * Drop the given table if it exists
	 * @param tableName
	 */
	public void dropTable(String tableName) {
		try {
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();
			String sql = "DROP TABLE if exists " + tableName + ";";

			//System.out.println(sql);
			stmt.execute(sql);
			conn.close();
		} catch (SQLException e) {
			System.out.println("could not drop table \n\t" + e.getMessage());
		}
	}

	/*
	 * Create a table with a primary key. If no columns are initially passed to the method, 
	 * nothing is done and the table will be created later.
	 * @param tableName
	 * @param indexedColumns
	 * @param pKey
	 */
	public void createTable(String tableName, Set<String> indexedColumns,
			String pKey) {

		StringBuffer sb = new StringBuffer();
		try {
			if(indexedColumns != null){
				Connection conn = openConnection();
	
				Statement stmt = conn.createStatement();
	
				sb.append("CREATE TABLE if not exists " + tableName + "(");
				Iterator<String> it = indexedColumns.iterator();
				for (int i = 0; i < indexedColumns.size(); i++) {
					String column = it.next().toString();
					sb.append(column);
					sb.append(" varchar(100), ");
	
				}
				sb.append("primary key(" + pKey + "));");
				//System.out.println(sb.toString());
				stmt.execute(sb.toString());
				conn.close();
			}
		} catch (SQLException e) {
			System.out.println("could not create the table\n\t"
					+ e.getMessage() + "\n\t" + sb.toString());
		}

	}
	
	/*
	 * Alters a table to add a set of columns
	 * @param tableName
	 * @param columns
	 */
	public void alterTable(String tableName, Set<String> columns){
		StringBuffer sb = new StringBuffer();
		try {
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();

			sb.append("ALTER TABLE " + tableName + " ADD(");
			Iterator<String> it = columns.iterator();
			for (int i = 0; i < columns.size() -1; i++) {
				String column = it.next().toString();
				sb.append(column);
				sb.append(" varchar(100), ");
			}
			if (it.hasNext()){
				String column = it.next().toString();
				sb.append(column);
				sb.append(" varchar(100)");
			}
			sb.append(");");
			
			//System.out.println(sb.toString());
			stmt.execute(sb.toString());

			conn.close();
		} catch (SQLException e) {
			System.out.println("could not alter the table\n\t"
					+ e.getMessage() + "\n\t" + sb.toString());
		}
	}
	
	/*
	 * will be overridden
	 */
	public Set<String> getColumns(String tableName) {
		return null;
	}


	/*
	 * Delete a row from a table 
	 * @param tableName
	 * @param rowKey
	 */
	@Override
	public void deleteRow(String tableName, Object rowKey) {

		try {
			String pKey = getTablePrimaryKeyName(tableName);
			Object rowKey_original = rowKey;
			rowKey = formatValue(rowKey);
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();
			StringBuffer sb = new StringBuffer();
			sb.append("delete FROM " + tableName);
			if (rowKey != null && !pKey.equals(""))
				sb.append(" where " + pKey + "=" + rowKey.toString());
			sb.append(";");
			//System.out.println(sb.toString());
			stmt.execute(sb.toString());

			conn.close();

			Set<Object> set = new HashSet<Object>();
			set.add(rowKey_original);
			notify(tableName, StorageSourceNotification.Action.DELETE, set);
		} catch (SQLException e) {
			System.out.println("******could not delete row\n" + e.getMessage());
		}

	}

	/*
	 * Delete from <tableName> where <columnName> = <key>
	 * @param tableName
	 * @param columnName
	 * @param key
	 */
	public void deleteRow(String tableName, String columnName, Object key) {

		try {
			Object key_original = key;
			key = formatValue(key);
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();
			String sql = "delete FROM " + tableName + " where " + columnName
					+ "=" + key + ";";
			//System.out.println(sql);
			stmt.execute(sql);

			Set<Object> set = new HashSet<Object>();
			set.add(key_original);
			notify(tableName, StorageSourceNotification.Action.DELETE, set);
			conn.close();
		} catch (SQLException e) {
			System.out.println("could not delete row\n" + e.getMessage());
		}
	}

	/*
	 * Casts an IQuery to SQLQuery and executes it
	 * @param query
	 */
	@Override
	public IResultSet executeQuery(IQuery query) {
		IResultSet rs = executeQuery((SqlQuery) query);
		return rs;
	}

	/*
	 * Execute a SQLQuery object. See createQuery for what goes into query object.
	 * @param query
	 */
	public SqlResultSet executeQuery(SqlQuery query) {
		SqlResultSet rs = null;
		query = (SqlQuery) query;
		StringBuffer sb = new StringBuffer();
		try {
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();

			String columns = "";
			String predicates = "";
			String order = "";

			if (query.getColumnNameList() != null) {
				Set<String> columnNameList = new HashSet<String>();
				for (String c : query.getColumnNameList())
					columnNameList.add(c);
				addMissingColumns(query.getTableName(), columnNameList);
				
				String[] columnList = query.getColumnNameList();
				for (int i = 0; i < columnList.length - 1; i++)
					columns += columnList[i] + ", ";
				columns += columnList[columnList.length - 1];
			} else {
				columns = "*";
			}

			if (query.getPredicate() != null) {
				Map<String, Comparable<?>> map = null;
				if (query.getParameterMap() != null) {
					map = query.getParameterMap();
				}
				predicates = createPredicateString(map, query.getPredicate());
			}

			if (query.getRowOrdering() != null) {
				List<Item> list = query.getRowOrdering().getItemList();
				for (int i = 0; i < list.size(); i++) {
					String column = list.get(i).getColumn();
					String dir = list.get(i).getDirection().toString();
					if (dir.equals("DESCENDING"))
						dir = "DESC";
					else
						dir = "ASC";
					order += column + " " + dir;
					if (i < list.size() - 1)
						order += ",";
				}
			}

			if (columns.equals(""))
				columns = "*";
			
			sb.append("Select " + columns + " from " + query.getTableName());
			if (!predicates.equals("")) {
				sb.append(" where " + predicates);
			}
			if (!order.equals(""))
				sb.append(" order by " + order);
			sb.append(";");

			//System.out.println("\t\t" + sb.toString());
			ResultSet sqlresult = stmt.executeQuery(sb.toString());
			ResultSetMetaData rsmeta = sqlresult.getMetaData();
			List<Map<String, Object>> rowList = new ArrayList<Map<String, Object>>();

			while (sqlresult.next()) {
				Map<String, Object> map = new HashMap<String, Object>();

				for (int i = 0; i < rsmeta.getColumnCount(); i++) {
					map.put(rsmeta.getColumnName(i + 1), sqlresult
							.getObject(i + 1));
				}
				//System.out.println("\t " + map);
				rowList.add(map);
			}
			rs = new SqlResultSet(this, query.getTableName(), rowList);

			conn.close();
		} catch (SQLException e) {
			System.out.println("failed to execute query: " + sb.toString() + "\n"
					+ e.getMessage());
		}
		return rs;
	}

	/*
	 * Gets a single row from a table
	 * @param tableName
	 * @param rowKey
	 */
	@Override
	public SqlResultSet getRow(String tableName, Object rowKey) {
		String pKey = getTablePrimaryKeyName(tableName);
		SqlResultSet rs = getRow(tableName, pKey, rowKey);
		return rs;
	}

	/*
	 * Gets a single row from a table
	 * @param tableName
	 * @param columnName
	 * @param rowKey
	 */
	public SqlResultSet getRow(String tableName, String columnName,
			Object rowkey) {
		SqlResultSet rs = null;
		String[] columns = { "*" };
		String pKey = getTablePrimaryKeyName(tableName);
		Comparable<?> key = (Comparable<?>) rowkey;
		IPredicate predicate = new OperatorPredicate(columnName,
				OperatorPredicate.Operator.EQ, key);
		SqlQuery query = new SqlQuery(tableName, columns, predicate, null);
		query.setParameter(pKey, rowkey);
		rs = executeQuery(query);

		return rs;
	}
	
	/*
	 * Checks a table to see if a set of columns is present and adds the ones that aren't.
	 * @param tableName
	 * @param columns
	 */
	public void addMissingColumns(String tableName, Set<String> columns){
		Set<String> tableColumns = getColumns(tableName);
		Set<String> missing = new HashSet<String>();
		for(String c : columns){
			if (!tableColumns.contains(c))
				missing.add(c);
		}
		if (!missing.isEmpty())
			alterTable(tableName, missing);
	}

	/*
	 * Inserts a row (Insert into <tableName>(column1, column2, ...) values(value1, value2, ....))
	 * @param tableName
	 * @param values
	 */
	@Override
	public void insertRow(String tableName, Map<String, Object> values) {
		Map<String, Object> values_original = values;
		try {
			Connection conn = openConnection();
			Statement stmt = conn.createStatement();
			values = formatValues(values);
			
			addMissingColumns(tableName, values.keySet());

			StringBuffer sb = new StringBuffer();
			StringBuffer objects = new StringBuffer();
			sb.append("Insert into " + tableName + "(");
			objects.append(" values(");
			Iterator<String> it = values.keySet().iterator();
			for (int i = 0; i < values.size() - 1; i++) {
				String key = it.next();
				sb.append(key + ", ");
				objects.append(values.get(key) + ", ");
			}
			String key = it.next().toString();
			objects.append(values.get(key) + ")");
			sb.append(key + ") " + objects.toString() + ";");

			//System.out.println("\t\t\t" + sb.toString());
			stmt.execute(sb.toString());

			Set<Object> set = new HashSet<Object>();
			set.add(values_original.get(getTablePrimaryKeyName(tableName)));
			notify(tableName, StorageSourceNotification.Action.MODIFY, set);
			conn.close();
		} catch (SQLException e) {
			System.out.println("failed to insert row\n" + e.getMessage()
					+ "\n\t updating row instead");
			if (e.getMessage().contains("not unique")) {
				String key = getTablePrimaryKeyName(tableName);
				Object rowKey = values_original.get(key);
				updateRow(tableName, rowKey, values_original);
			}
			else if (e.getMessage().contains("doesn't exist") || e.getMessage().contains("no such table")){
				createTable(tableName, values_original.keySet());
				insertRow(tableName, values_original);
			}
		}

	}

	@Override
	public void setTablePrimaryKeyName(String tableName, String primaryKeyName) {
	}

	/*
	 * updates a row with the given values
	 * @param tableName
	 * @param rowKey
	 * @param values
	 */
	@Override
	public void updateRow(String tableName, Object rowKey,
			Map<String, Object> values) {
		
		StringBuffer sb = new StringBuffer();
		Map<String, Object> values_original = values;
		Object rowKey_original = rowKey;
		try {
			addMissingColumns(tableName, values.keySet());
			
			String pKey = getTablePrimaryKeyName(tableName);
			IPredicate p = new OperatorPredicate(pKey, OperatorPredicate.Operator.EQ, rowKey.toString());
			IResultSet rs = executeQuery(tableName, null, p, null);
			if (rs.next()){
				//System.out.println("****************************UPDATING*************************");
				Connection conn = openConnection();
				Statement stmt = conn.createStatement();
				rowKey = formatValue(rowKey);
				values = formatValues(values);
	
				sb.append("update " + tableName + " set ");
				Iterator<String> it = values.keySet().iterator();
				for (int i = 0; i < values.size(); i++) {
					String key = it.next();
					sb.append(key + "=" + values.get(key) + ", ");
				}
				sb.deleteCharAt(sb.length()-2);
				sb.append("WHERE " + pKey + "=" + rowKey + ";");
				
				//System.out.println("\t\t" + sb.toString());
				stmt.execute(sb.toString());
	
				Set<Object> set = new HashSet<Object>();
				set.add(rowKey_original);
				notify(tableName, StorageSourceNotification.Action.MODIFY, set);
				conn.close();
			}else{
				//System.out.println("****************************INSERTING*************************");
				insertRow(tableName, values);
			}

		} catch (SQLException e) {
			System.out.println("failed to update row\n" + e.getMessage() + "\n\t" + sb.toString());
			
			if (e.getMessage().contains("doesn't exist") || e.getMessage().contains("no such table")){
				createTable(tableName, values_original.keySet());
				updateRow(tableName, rowKey_original, values_original);
			}
		}
	}

	@Override
	public void updateRow(String tableName, Map<String, Object> values) {
		String key = getTablePrimaryKeyName(tableName);
		Object rowKey = values.get(key);
		updateRow(tableName, rowKey, values);
	}

	public void updateRows(String tableName, Set<Object> rowKeys,
			Map<String, Object> values) {
		Iterator<Object> it = rowKeys.iterator();
		for (int i = 0; i < rowKeys.size(); i++) {
			updateRow(tableName, it.next(), values);
		}
	}

	public void updateRows(String tableName, List<Map<String, Object>> values) {
		for (int i = 0; i < values.size(); i++) {
			updateRow(tableName, values.get(i));
		}
	}

	public void updateRows(String tableName, IPredicate predicate,
			Map<String, Object> values) {
		String primaryKeyName = getTablePrimaryKeyName(tableName);
		String[] columnNameList = { primaryKeyName };
		IResultSet resultSet = executeQuery(tableName, columnNameList,
				predicate, null);
		Set<Object> rowKeys = new HashSet<Object>();
		while (resultSet.next()) {
			String rowKey = resultSet.getString(primaryKeyName);
			rowKeys.add(rowKey);
		}
		updateRows(tableName, rowKeys, values);

	}

	public void deleteRows(String tableName, IPredicate predicate) {
		IResultSet resultSet = null;
		try {
			resultSet = executeQuery(tableName, null, predicate, null);
			while (resultSet.next()) {
				resultSet.deleteRow();
			}
			resultSet.save();
		} finally {
			if (resultSet != null)
				resultSet.close();
		}
	}

	public void deleteRows(String tableName) {
		try {
			String[] key = { getTablePrimaryKeyName(tableName) };
			SqlResultSet rs = (SqlResultSet) executeQuery(tableName, key, null,
					null);
			Set<Object> set = new HashSet<Object>();
			while (rs.next()) {
				set.add(rs.getObject(key[0]));
			}

			Connection conn = openConnection();
			Statement stmt = conn.createStatement();
			String sql = "delete from " + tableName + ";";
			// System.out.println(sql);
			stmt.execute(sql);

			notify(tableName, StorageSourceNotification.Action.DELETE, set);
			conn.close();
		} catch (SQLException e) {
			System.out.println("could not delete rows\n\t" + e.getMessage());
		}
	}

	public void deleteRows(String tableName, Set<Object> deleteSet) {
		if (deleteSet != null){
			Iterator<Object> i = deleteSet.iterator();
			Object rowKey = null;
			while (i.hasNext()) {
				rowKey = i.next();
				deleteRow(tableName, rowKey);
			}
		}else{
			deleteRows(tableName);
		}

	}

	public String getTablePrimaryKeyName(String tableName) {
		String pKey = "";
		return pKey;
	}

	public Map<String, Object> formatValues(Map<String, Object> values) {
		return values;
	}

	public Object formatValue(Object value) {
		return value;
	}

	/*
	 * Creates a predicate string (i.e. Where x = y)
	 * Can handle both operator and compound predicates
	 * Will first try and cast as CompoundPredicate. Will then recursively call this method on each predicate
	 * in the compound predicate's list and add those to the string. 
	 * If an exception is thrown when casting, we can cast as
	 * an operator predicate and create our string.
	 * @param map
	 * @param predicate 
	 */
	public String createPredicateString(Map<String, Comparable<?>> map,
			IPredicate predicate) {
		StringBuffer predS = new StringBuffer();
		try {
			CompoundPredicate cp = (CompoundPredicate) predicate;
			for (IPredicate list : cp.getPredicateList()) {
				predS .append(createPredicateString(map, list) + " "
						+ cp.getOperator() + " ");
			}
		} catch (ClassCastException e) {
			OperatorPredicate op = (OperatorPredicate) predicate;
			Object value = op.getValue();
			if (value != null && value.toString().charAt(0) == '?')
				value = value.toString().substring(1,
						value.toString().length() - 1);
			else if (value == null)
				value = "NULL";
			if (map != null && map.containsKey(value)) {
				value = map.get(value);
			}
			String operator = "";
			switch (op.getOperator()) {
			case EQ:
				operator = "=";
				break;
			case LT:
				operator = "<";
				break;
			case LTE:
				operator = "<=";
				break;
			case GT:
				operator = ">";
				break;
			case GTE:
				operator = ">=";
				break;
			default:
				operator = "=";
				break;
			}
			value = formatValue(value);
			if (value.equals("NULL"))
				operator = "is";

			predS.append(op.getColumnName() + " " + operator + " "
					+ value.toString());
		}
		if (predS.toString().contains("AND") || predS.toString().contains("OR"))
			predS = predS.delete(predS.length() - 4, predS.length());

		return predS.toString();
	}

	public void notify(String tableName, Action action, Set<Object> keys) {
		StorageSourceNotification s = new StorageSourceNotification(tableName,
				action, keys);
		notifyListeners(s);
		// System.out.println(s.toString());
	}

	@Override
	protected void deleteRowImpl(String tableName, Object rowKey) {
		deleteRow(tableName, rowKey);
		
	}

	@Override
	protected void deleteRowsImpl(String tableName, Set<Object> rowKeys) {
		deleteRows(tableName, rowKeys);
		
	}

	@Override
	protected IResultSet executeQueryImpl(IQuery query) {
		SqlResultSet rs = (SqlResultSet) executeQuery(query);
		return rs;
	}

	@Override
	protected IResultSet getRowImpl(String tableName, Object rowKey) {
		SqlResultSet rs = (SqlResultSet) getRow(tableName, rowKey);
		return rs;
	}

	@Override
	protected void insertRowImpl(String tableName, Map<String, Object> values) {
		insertRow(tableName, values);
		
	}

	@Override
	protected void updateMatchingRowsImpl(String tableName,
			IPredicate predicate, Map<String, Object> values) {
		updateRows(tableName, predicate, values);
		
	}

	@Override
	protected void updateRowImpl(String tableName, Object rowKey,
			Map<String, Object> values) {
		updateRow(tableName, rowKey, values);
		
	}

	@Override
	protected void updateRowImpl(String tableName, Map<String, Object> values) {
		updateRow(tableName, values);
		
	}

	@Override
	protected void updateRowsImpl(String tableName,
			List<Map<String, Object>> rows) {
		updateRows(tableName, rows);
		
	}

}
