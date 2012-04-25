/**
 *    Created by Andrew Freitas
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;

public class MySQLStorageSource extends SQLStorageSource {

	private static String DB_PATH = "localhost:3306/";
	private static String DB_NAME = "floodlight";
	private static String DB_USER = "root";
	private static String DB_PASS = "root";
	
	public void init(FloodlightModuleContext context) throws FloodlightModuleException{
		Map<String, String> configOptions = context.getConfigParams(this);
        String s = configOptions.get("DB_PATH");
        if (s != null)
        	DB_PATH = s;
        s = configOptions.get("DB_NAME");
        if (s != null)
        	DB_NAME = s;
        s = configOptions.get("DB_USER");
        if (s != null)
        	DB_USER = s;
        s = configOptions.get("DB_PASS");
        if (s!= null)
        	DB_PASS = s;
        logger.info("MySQL database path set to " + DB_PATH + DB_NAME);
        
        super.init(context);
	}
	  
        
	@Override
	public Connection openConnection() {
		Connection conn = null;
		try {
			String driverName = "com.mysql.jdbc.Driver";
			
			try {
				Class.forName(driverName).newInstance();
				String url = "jdbc:mysql://" + DB_PATH + DB_NAME; // provide jdbc with path to db file
				conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
			
		} catch (ClassNotFoundException e) {
			log.error("Could not find database driver " + e.getMessage());
		} catch (SQLException e) {
			log.error("Could not connect to database " + e.getMessage());
			if (e.getMessage().contains("Unknown database")){
				log.info("Creating new database " + DB_NAME);
				conn = createDatabase();
			}
		}
		return conn;
	}
		
	public Connection createDatabase(){
		Connection conn = null;
		try{
			String driverName = "com.mysql.jdbc.Driver";
			try {
				Class.forName(driverName).newInstance();
				String url = "jdbc:mysql://" + DB_PATH; // provide jdbc with path to db file
				conn = DriverManager.getConnection(url, DB_USER, DB_PASS);
				Statement stmt = conn.createStatement();
				String sql = "Create database " + DB_NAME + ";";
				stmt.execute(sql);
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
			
		} catch (ClassNotFoundException e) {
			log.error("Could not find database driver " + e.getMessage());
		} catch (SQLException e) {
			log.error("Could not connect to database " + e.getMessage());
		}
		return conn;
	}

	@Override
	public String getTablePrimaryKeyName(String tableName) {
		String pKey = "";
		try {
			Connection conn = openConnection();
			Statement stmt = conn.createStatement();

			StringBuffer sb = new StringBuffer();
			sb.append("show keys from " + tableName + " where key_name = 'primary'");
			//System.out.println(sql);
			ResultSet sqlresult = stmt.executeQuery(sb.toString());

			if (sqlresult.next())
				pKey = sqlresult.getNString("Column_name");

			conn.close();
		} catch (SQLException e) {
			if (e.getMessage().contains("doesn't exist")){
				log.info("Could not get primary becuase one has not been set yet");
			}else
				log.error("Could not get primary key " + e.getMessage());
		}
		return pKey;
	}
	
	@Override
	public void setTablePrimaryKeyName(String tableName, String primaryKeyName) {
		try{
			Connection conn = openConnection();
			Statement stmt = conn.createStatement();
			
			String sql = "alter table " + tableName + " drop primary key;";
			String sql2 = "alter table " + tableName + " add primary key(" + primaryKeyName + ");";
			
			if (!getTablePrimaryKeyName(tableName).equals(""))
				stmt.execute(sql);
			stmt.execute(sql2);
			
			conn.close();
		}catch(SQLException e){
			if (e.getMessage().contains("doesn't exist")){
				Set<String> columns = new HashSet<String>();
				columns.add(primaryKeyName);
				createTable(tableName, columns, primaryKeyName);
			}else{
				log.error("Could not set primary key " + e.getMessage());
			}
		}
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
			if (value.toString().equals("true"))
				value = 1;
			else if (value.toString().equals("false"))
				value = 0;
			else if (value.getClass() == java.util.Date.class){
				value = (Date) value;
				SimpleDateFormat dateFormat = new SimpleDateFormat(
					"yyyy-MM-dd HH:mm:ss.SSS");
				dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
				value = "'" + dateFormat.format(value) + "'";
			}
			else{
				if (!value.toString().equals("null")
					&& !value.toString().equals("NULL")) {
					if (!value.toString().contains("'"))
						value = "'" + value.toString() + "'";
				}
			}
		} catch (NullPointerException e) {
			//System.out.println("SQL formatting: value was null");
			return "null";
		}
		return value;
	}
	
	@Override
	public Set<String> getColumns(String tableName){
		StringBuffer sb = new StringBuffer();
		Set<String> columns = new HashSet<String>();
		try {
			Connection conn = openConnection();

			Statement stmt = conn.createStatement();

			sb.append("SHOW columns FROM " + tableName + ";");
			ResultSet rs = stmt.executeQuery(sb.toString());
			while(rs.next()){
				columns.add(rs.getString("Field"));
			}
		}catch(SQLException e){
			log.error("Could not get columns for table " + tableName + " " + e.getMessage());
		}
		return columns;
	}

}
