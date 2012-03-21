package net.floodlightcontroller.storage.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MySQLStorageSource extends SQLStorageSource {

	/**
	 * we may want to move the information in this method to another file
	 * (possibly password protected) and only leave the implementation here
	 * 
	 * @return
	 */
	private Connection openConnection() {
		Connection conn = null;
		try {
			String driverName = "com.mysql.jdbc.Driver";
			
			try {
				Class.forName(driverName).newInstance();
				// String serverName = "localhost";
				String mydb = "/openflow";
				String username = "root";
				String password = "root";
				String url = "jdbc:mysql://" + mydb; // provide jdbc with path to db file
				conn = DriverManager.getConnection(url, username, password);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
			
		} catch (ClassNotFoundException e) {
			System.out.println("could not find database driver");
		} catch (SQLException e) {
			System.out.println("could not connect to database\n"
					+ e.getMessage());
		}
		return conn;
	}

	@Override
	public String getTablePrimaryKeyName(String tableName) {
		String pKey = "";
		try {
			Connection conn = openConnection();
			Statement stmt = conn.createStatement();

			String sql = "show keys from " + tableName + " where key_name = 'primary'";
			//System.out.println(sql);
			ResultSet sqlresult = stmt.executeQuery(sql);

			pKey = sqlresult.getString(4);

			conn.close();
		} catch (SQLException e) {
			System.out.println("failed to get primary key\n" + e.getMessage());
		}
		return pKey;
	}

	@Override
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

	@Override
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
			System.out.println(value);
		}
		return value;
	}

}
