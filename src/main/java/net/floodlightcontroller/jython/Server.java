package net.floodlightcontroller.jython;

import java.util.Map;

import org.python.util.PythonInterpreter;

public class Server extends Thread {
	int port;
	Map<String, Object> locals;
	
	public Server(int port_, Map<String, Object> locals_) {
		this.port = port_ ;
		this.locals = locals_;
		
	}

    public void run() {
        PythonInterpreter p = new PythonInterpreter();
        for (String name : this.locals.keySet()) {
            p.set(name, this.locals.get(name));
        }

        String jarPath = Server.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        p.exec("import sys");
        p.exec("sys.path.append('" + jarPath + "')");
        p.exec("from debugserver import run_server");
        p.exec("run_server(" + this.port + ", '0.0.0.0', locals())");
    }

}
