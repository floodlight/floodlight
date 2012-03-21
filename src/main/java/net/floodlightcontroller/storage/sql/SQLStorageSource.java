package net.floodlightcontroller.storage.sql;

import java.sql.Connection;
import java.sql.DriverManager;
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

	/**
	 * we may want to move the information in this method to another file
	 * (possibly password protected) and only leave the implementation here
	 * 
	 * @return
	 */
	private Connection openConnection() {
		Connection conn = null;
		try {
			String driverName = "org.sqlite.JDBC";
			Class.forName(driverName);

			// String serverName = "localhost";
			String mydb = "/home/andrew/School/CSC 492/openflow.db";
			String url = "jdbc:sqlite:" + mydb; // provide jdbc with path to db
												// file
			// String username = "root";
			// String password = "root";
			conn = DriverManager.getConnection(url);
		} catch (ClassNotFoundException e) {
			System.out.println("could not find database driver");
		} catch (SQLException e) {
			System.out.println("could not connect to database\n"
					+ e.getMessage());
		}
		return conn;
	}

	@Override
	public IQuery createQuery(String tableName, String[] columnNames,
			IPredicate predicate, RowOrdering ordering) {
		IQuery query = new SqlQuery(tableName, columnNames, predicate, ordering);

		return query;
	}

	@Override
	public void createTable(String tableName, Set<String> indexedColumns) {

		try {
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();
			String sql = "CREATE TABLE if not exists " + tableName + "(";
			Iterator<String> it = indexedColumns.iterator();
			for (int i = 0; i < indexedColumns.size() - 1; i++) {
				sql += it.next().toString() + " varchar(100), ";
			}
			sql += it.next().toString() + " text);";
			
			//System.out.println(sql);
			stmt.executeUpdate(sql);

			conn.close();
		} catch (SQLException e) {
			System.out.println("could not create the table\n\t"
					+ e.getMessage());
		}

	}
	
	public void dropTable(String tableName) {
		try {
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();
			String sql = "DROP TABLE if exists " + tableName + ";";

			stmt.executeUpdate(sql);
			conn.close();
		} catch (SQLException e) {
			System.out.println("could not drop table \n\t" + e.getMessage());
		}
	}

	public void createTable(String tableName, Set<String> indexedColumns,
			String pKey) {

		try {
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();
			String sql = "CREATE TABLE if not exists " + tableName + "(";
			Iterator<String> it = indexedColumns.iterator();
			for (int i = 0; i < indexedColumns.size(); i++) {
				String column = it.next().toString();
				sql += column;
				if (column.equals(pKey))
					sql += " varchar(100), ";
				else
					sql += " text, ";
			}
			sql += "primary key (" + pKey + "));";
			//System.out.println(sql);
			stmt.executeUpdate(sql);

			conn.close();
		} catch (SQLException e) {
			System.out.println("could not create the table\n\t"
					+ e.getMessage());
		}

	}

	@Override
	public void deleteRow(String tableName, Object rowKey) {

		try {
			String pKey = getTablePrimaryKeyName(tableName);
			Object rowKey_original = rowKey;
			rowKey = formatValue(rowKey);
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();
			String sql = "delete FROM " + tableName + " where " + pKey + "="
					+ rowKey.toString() + ";";
			// System.out.println(sql);
			stmt.executeUpdate(sql);

			conn.close();

			Set<Object> set = new HashSet<Object>();
			set.add(rowKey_original);
			notify(tableName, StorageSourceNotification.Action.DELETE, set);
		} catch (SQLException e) {
			System.out.println("could not delete row\n" + e.getMessage());
		}

	}

	public void deleteRow(String tableName, String columnName, Object key) {

		try {
			Object key_original = key;
			key = formatValue(key);
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();
			String sql = "delete FROM " + tableName + " where " + columnName
					+ "=" + key + ";";
			// System.out.println(sql);
			stmt.executeUpdate(sql);

			Set<Object> set = new HashSet<Object>();
			set.add(key_original);
			notify(tableName, StorageSourceNotification.Action.DELETE, set);
			conn.close();
		} catch (SQLException e) {
			System.out.println("could not delete row\n" + e.getMessage());
		}
	}

	@Override
	public IResultSet executeQuery(IQuery query) {
		IResultSet rs = executeQuery((SqlQuery) query);
		return rs;
	}

	public SqlResultSet executeQuery(SqlQuery query) {
		SqlResultSet rs = null;
		query = (SqlQuery) query;
		String sql = "";
		try {
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();

			String columns = "";
			String predicates = "";
			String order = "";

			if (query.getColumnNameList() != null) {
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

			sql = "Select " + columns + " from " + query.getTableName();
			if (!predicates.equals("")) {
				sql += " where " + predicates;
			}
			if (!order.equals(""))
				sql += " order by " + order;
			sql += ";";

			//System.out.println(sql);
			ResultSet sqlresult = stmt.executeQuery(sql);
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
			System.out.println("failed to execute query: " + sql + "\n"
					+ e.getMessage());
		}
		return rs;
	}

	@Override
	public SqlResultSet getRow(String tableName, Object rowKey) {
		String pKey = getTablePrimaryKeyName(tableName);
		SqlResultSet rs = getRow(tableName, pKey, rowKey);
		return rs;
	}

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

	@Override
	public void insertRow(String tableName, Map<String, Object> values) {
		Map<String, Object> values_original = values;
		try {
			Connection conn = openConnection();
			Statement stmt = conn.createStatement();
			values = formatValues(values);

			String sql = "Insert into " + tableName + "(";
			String objects = " values(";
			Iterator<String> it = values.keySet().iterator();
			for (int i = 0; i < values.size() - 1; i++) {
				String key = it.next();
				sql += key + ", ";
				objects += values.get(key) + ", ";
			}
			String key = it.next().toString();
			objects += values.get(key) + ")";
			sql += key + ") " + objects;

			//System.out.println(sql);
			stmt.execute(sql);

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
		}

	}

	@Override
	public void setTablePrimaryKeyName(String tableName, String primaryKeyName) {
	}

	@Override
	public void updateRow(String tableName, Object rowKey,
			Map<String, Object> values) {
		try {
			String pKey = getTablePrimaryKeyName(tableName);
			Connection conn = openConnection();
			Statement stmt = conn.createStatement();
			Object rowKey_original = rowKey;
			rowKey = formatValue(rowKey);
			values = formatValues(values);

			String sql = "Insert or replace into " + tableName + "(";
			String objects = " values(";
			Iterator<String> it = values.keySet().iterator();
			for (int i = 0; i < values.size() - 1; i++) {
				String key = it.next();
				sql += key + ", ";
				objects += values.get(key) + ", ";
			}
			String key = it.next().toString();
			objects += values.get(key) + ")";
			sql += key + ") " + objects + ";";
			
			//System.out.println(sql);
			stmt.execute(sql);

			Set<Object> set = new HashSet<Object>();
			set.add(rowKey_original);
			notify(tableName, StorageSourceNotification.Action.MODIFY, set);
			conn.close();

		} catch (SQLException e) {
			System.out.println("failed to update row\n" + e.getMessage());
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
		Iterator<Object> i = deleteSet.iterator();
		Object rowKey = null;
		while (i.hasNext()) {
			rowKey = i.next();
			deleteRow(tableName, rowKey);
		}

	}

	public String getTablePrimaryKeyName(String tableName) {
		String pKey = "";
		try {
			Connection conn = openConnection();
			Statement stmt = conn.createStatement();

			String sql = "pragma table_info(" + tableName + ");";
			// System.out.println(sql);
			ResultSet sqlresult = stmt.executeQuery(sql);

			while (pKey == "" && sqlresult.next()) {
				if (sqlresult.getInt(6) == 1) {
					pKey = sqlresult.getString(2);
				}

			}

			conn.close();
		} catch (SQLException e) {
			System.out.println("failed to get primary key\n" + e.getMessage());
		}
		return pKey;
	}

	public Map<String, Object> formatValues(Map<String, Object> values) {
		Iterator<String> it = values.keySet().iterator();
		Object value = null;
		Map<String, Object> newValues = new HashMap<String, Object>();
		for (int i = 0; i < values.size(); i++) {
			String key = it.next();
			value = values.get(key);
			value = formatValue(value);
			newValues.put(key, value);
		}
		return newValues;
	}

	public Object formatValue(Object value) {
		try {
			if (value.getClass().equals(java.lang.String.class)
					&& !value.toString().equals("null")
					&& !value.toString().equals("NULL")) {
				if (!value.toString().contains("'"))
					value = "'" + value.toString() + "'";
			} else if (value.toString().equals("true"))
				value = 1;
			else if (value.toString().equals("false"))
				value = 0;
		} catch (NullPointerException e) {
			System.out.println(value + " could not be formatted. It is null.");
		}
		return value;
	}

	public String createPredicateString(Map<String, Comparable<?>> map,
			IPredicate predicate) {
		String predS = "";
		try {
			CompoundPredicate cp = (CompoundPredicate) predicate;
			for (IPredicate list : cp.getPredicateList()) {
				predS += createPredicateString(map, list) + " "
						+ cp.getOperator() + " ";
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

			predS = op.getColumnName() + " " + operator + " "
					+ value.toString();
		}
		if (predS.contains("AND") || predS.contains("OR"))
			predS = predS.substring(0, predS.length() - 4);

		return predS;
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
