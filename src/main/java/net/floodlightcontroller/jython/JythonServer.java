/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.jython;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class starts a thread that runs a jython interpreter that
 * can be used for debug (or even development).
 *
 * @author mandeepdhami
 *
 */
public class JythonServer extends Thread {
    protected static Logger log = LoggerFactory.getLogger(JythonServer.class);

	int port;
	Map<String, Object> locals;
	
	/**
	 * @param port_ Port to use for jython server
	 * @param locals_ Locals to add to the interpreters top level name space
	 */
	public JythonServer(int port_, Map<String, Object> locals_) {
		this.port = port_ ;
		this.locals = locals_;
		if (this.locals == null) {
			this.locals = new HashMap<String, Object>();
		}
		this.locals.put("log", JythonServer.log);
		this.setName("debugserver");
	}

    /**
     * The main thread for this class invoked by Thread.run()
     *
     * @see java.lang.Thread#run()
     */
    public void run() {
        PythonInterpreter p = new PythonInterpreter();
        for (String name : this.locals.keySet()) {
            p.set(name, this.locals.get(name));
        }

        URL jarUrl = JythonServer.class.getProtectionDomain().getCodeSource().getLocation();
        String jarPath = jarUrl.getPath();
        if (jarUrl.getProtocol().equals("file")) {
            // If URL is of type file, assume that we are in dev env and set path to python dir.
            // else use the jar file as is
            jarPath = jarPath + "../../src/main/python/";
        }

        p.exec("import sys");
        p.exec("sys.path.append('" + jarPath + "')");
        p.exec("from debugserver import run_server");
        p.exec("run_server(" + this.port + ", '0.0.0.0', locals())");
    }

}
