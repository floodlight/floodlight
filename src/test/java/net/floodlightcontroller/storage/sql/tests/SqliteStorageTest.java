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

package net.floodlightcontroller.storage.sql.tests;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestApiServer;
import net.floodlightcontroller.storage.sql.SqliteStorageSource;
import org.junit.Before;

public class SqliteStorageTest extends SQLStorageTest {

	@Before
	public void setUp() throws Exception {
		storageSource = new SqliteStorageSource();
        restApi = new RestApiServer();
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IRestApiService.class, restApi);
        fmc.addService(SqliteStorageSource.class, storageSource);
        restApi.init(fmc);
        storageSource.init(fmc);
        restApi.startUp(fmc);
        storageSource.startUp(fmc);
		super.setUp();
	}
}
