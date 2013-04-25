package org.sdnplatform.sync.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map.Entry;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.threadpool.ThreadPool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonParser;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.UnknownStoreException;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.remote.RemoteSyncManager;

public class SyncClient {
    RemoteSyncManager syncManager;
    IStoreClient<JsonNode, JsonNode> storeClient;

    /**
     * Shell commands
     */
    protected HashMap<String, ShellCommand> commands;

    /**
     * Command-line settings
     */
    protected SyncClientSettings settings;

    /**
     * Stream to use for output
     */
    protected PrintStream out = System.out;

    /**
     * Stream to use for errors
     */
    protected PrintStream err = System.err;
    
    public SyncClient(SyncClientSettings settings) {
        this.settings = settings;
        
        commands = new HashMap<String, ShellCommand>();
        commands.put("quit", new QuitCommand());
        commands.put("help", new HelpCommand());
        commands.put("put", new PutCommand());
        commands.put("delete", new DeleteCommand());
        commands.put("get", new GetCommand());
        commands.put("getfull", new GetFullCommand());
        commands.put("entries", new EntriesCommand());
        commands.put("store", new StoreCommand());
        commands.put("register", new RegisterCommand());
    }
    
    protected boolean connect() 
            throws Exception {
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        ThreadPool tp = new ThreadPool();
        syncManager = new RemoteSyncManager();
        fmc.addService(IThreadPoolService.class, tp);
        fmc.addService(ISyncService.class, syncManager);
        fmc.addConfigParam(syncManager, "hostname", settings.hostname);
        fmc.addConfigParam(syncManager, "port", 
                           Integer.toString(settings.port));
        if (settings.authScheme != null) {
            fmc.addConfigParam(syncManager, "authScheme", 
                               settings.authScheme.toString());
            fmc.addConfigParam(syncManager, "keyStorePath", settings.keyStorePath);
            fmc.addConfigParam(syncManager, "keyStorePassword", 
                               settings.keyStorePassword);
        }
        tp.init(fmc);
        syncManager.init(fmc);
        tp.startUp(fmc);
        syncManager.startUp(fmc);
        
        if (settings.storeName != null)
            getStoreClient();
        
        out.println("Connected to " + 
                settings.hostname + ":" + settings.port);
        return true;
    }
    
    protected void getStoreClient() 
            throws UnknownStoreException {
        storeClient = syncManager.getStoreClient(settings.storeName, 
                                                 JsonNode.class, 
                                                 JsonNode.class);
    }
    
    protected boolean checkStoreSettings() {
        if (settings.storeName == null) {
            err.println("No store selected.  Select using \"store\" command.");
            return false;
        }
        return true;
    }
    
    /**
     * Quit command
     * @author readams
     *
     */
    protected static class QuitCommand extends ShellCommand {
        @Override
        public boolean execute(String[] tokens, String line) {
            return true;
        }

        @Override
        public String syntaxString() {
            return "quit";
        }
    }
    
    /**
     * Help command
     * @author readams
     *
     */
    protected class HelpCommand extends ShellCommand {
        @Override
        public boolean execute(String[] tokens, String line) {
            out.println("Commands: ");
            for (Entry<String, ShellCommand> entry : commands.entrySet()) {
                out.println(entry.getValue().syntaxString());
            }
            return false;
        }

        @Override
        public String syntaxString() {
            return "help";
        }
    }

    /**
     * Get command
     * @author readams
     *
     */
    protected class GetCommand extends ShellCommand {
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

        @Override
        public boolean execute(String[] tokens, String line) throws Exception {
            if (tokens.length < 2) {
                err.println("Usage: " + syntaxString());
                return false;
            }
            if (!checkStoreSettings()) return false;
            
            StringReader sr = new StringReader(line);
            while (sr.read() != ' ');
            JsonParser jp = mjf.createJsonParser(sr);
                     
            JsonNode keyNode = validateJson(jp);
            if (keyNode == null) return false;
            
            out.println("Getting Key:");
            out.println(writer.writeValueAsString(keyNode));
            out.println("");
            Versioned<JsonNode> value = storeClient.get(keyNode);
            display(value);
            
            return false;
        }
        
        protected void display(Versioned<JsonNode> value) throws Exception {
            if (value.getValue() == null) {
                out.println("Not found");
            } else {
                out.println("Value:");
                out.println(writer.writeValueAsString(value.getValue()));
            }
        }

        @Override
        public String syntaxString() {
            return "get [key]";
        }
    }
    
    protected class GetFullCommand extends GetCommand {
        @Override
        protected void display(Versioned<JsonNode> value) throws Exception {
            if (value.getValue() == null) {
                out.println("Not found");
            } else {
                out.println("Version:");
                out.println(value.getVersion());
                out.println("Value:");
                out.println(writer.writeValueAsString(value.getValue()));
            }
        }
        
        @Override
        public String syntaxString() {
            return "getfull [key]";
        }
    }

    protected class EntriesCommand extends ShellCommand {
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();

        @Override
        public boolean execute(String[] tokens, String line) throws Exception {
            if (!checkStoreSettings()) return false;

            IClosableIterator<Entry<JsonNode, Versioned<JsonNode>>> iter = 
                    storeClient.entries();
            try {
                while (iter.hasNext()) {
                    Entry<JsonNode, Versioned<JsonNode>> e = iter.next();
                    display(e.getKey(), e.getValue());
                }
            } finally {
                iter.close();
            }
            return false;
        }
        

        protected void display(JsonNode keyNode,
                               Versioned<JsonNode> value) throws Exception {
            if (value.getValue() == null) return;
            ObjectNode n = mapper.createObjectNode();
            n.put("key", keyNode);
            n.put("value", value.getValue());
            out.println(writer.writeValueAsString(n));
        }
        
        @Override
        public String syntaxString() {
            return "entries";
        }
    }

    /**
     * Put command
     * @author readams
     *
     */
    protected class PutCommand extends ShellCommand {
        @Override
        public boolean execute(String[] tokens, String line) throws Exception {
            if (tokens.length < 3) {
                err.println("Usage: " + syntaxString());
                return false;

            }
            if (!checkStoreSettings()) return false;

            StringReader sr = new StringReader(line);
            while (sr.read() != ' ');
            JsonParser jp = mjf.createJsonParser(sr);
            
            JsonNode keyNode = validateJson(jp);
            if (keyNode == null) return false;
            JsonNode valueNode = validateJson(jp);
            if (valueNode == null) return false;

            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            out.println("Putting Key:");
            out.println(writer.writeValueAsString(keyNode));
            out.println("\nValue:");
            out.println(writer.writeValueAsString(valueNode));
            out.flush();

            storeClient.put(keyNode, valueNode);
            out.println("Success");
                        
            return false;
        }

        @Override
        public String syntaxString() {
            return "put [key] [value]";
        }
    }
    
    /**
     * Delete command
     * @author readams
     *
     */
    protected class DeleteCommand extends ShellCommand {
        @Override
        public boolean execute(String[] tokens, String line) throws Exception {
            if (tokens.length < 2) {
                err.println("Usage: " + syntaxString());
                return false;
            }
            if (!checkStoreSettings()) return false;

            StringReader sr = new StringReader(line);
            while (sr.read() != ' ');
            JsonParser jp = mjf.createJsonParser(sr);
                     
            JsonNode keyNode = validateJson(jp);
            if (keyNode == null) return false;
            
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            out.println("Deleting Key:");
            out.println(writer.writeValueAsString(keyNode));
            out.println("");
            
            storeClient.delete(keyNode);
            out.println("Success");

            return false;
        }

        @Override
        public String syntaxString() {
            return "delete [key]";
        }
    }
    
    /**
     * Choose the store
     * @author readams
     */
    protected class StoreCommand extends ShellCommand {

        @Override
        public boolean execute(String[] tokens, String line)
                throws Exception {
            if (tokens.length < 2) {
                err.println("Usage: " + syntaxString());
                return false;
            }
            
            settings.storeName = tokens[1];
            getStoreClient();
            return false;
        }

        @Override
        public String syntaxString() {
            return "store [storeName]";
        }
        
    }
    
    /**
     * Register a new store
     * @author readams
     */
    protected class RegisterCommand extends ShellCommand {

        @Override
        public boolean execute(String[] tokens, String line)
                throws Exception {
            if (tokens.length < 3) {
                err.println("Usage: " + syntaxString());
                return false;
            }
            Scope scope = Scope.LOCAL;
            if ("global".equals(tokens[2]))
                scope = Scope.GLOBAL;

            settings.storeName = tokens[1];
            syncManager.registerStore(settings.storeName, scope);
            getStoreClient();
            return false;
        }

        @Override
        public String syntaxString() {
            return "register [storeName] [local|global]";
        }
        
    }

    protected void cleanup() throws InterruptedException {
        syncManager.shutdown();
    }
    
    protected boolean executeCommandLine(String line) {
        String[] tokens = line.split("\\s+");
        if (tokens.length > 0) {
            ShellCommand command = commands.get(tokens[0]);
            if (command != null) {
                try {
                    if (command.execute(tokens, line))
                        return true;
                } catch (Exception e) {
                    err.println("Failed to execute command: " + 
                                       line);
                    if (settings.debug)
                        e.printStackTrace(err);
                    else
                        err.println(e.getClass().getSimpleName() + 
                                    ": " + e.getMessage());
                }
            } else {
                err.println("Unrecognized command: \"" + 
                        tokens[0] + "\""); 
            }
        }
        return false;
    }
    
    protected void startShell(SyncClientSettings settings) 
            throws InterruptedException {
        BufferedReader br = 
                new BufferedReader(new InputStreamReader(System.in));
        String line;
        try {
            while (true) {
                err.print("> ");
                line = br.readLine();
                if (line == null) break;
                if (executeCommandLine(line)) break;
            }
        } catch (IOException e) {
            err.println("Could not read input: " + e.getMessage());
        }
    }
    
    protected static class SyncClientSettings {
        @Option(name="--help", 
                usage="Server hostname")
        protected boolean help;
        
        @Option(name="--hostname", aliases="-h", 
                usage="Server hostname", required=true)
        protected String hostname;

        @Option(name="--port", aliases="-p", usage="Server port", required=true)
        protected int port;
        
        @Option(name="--store", aliases="-s", 
                usage="Store name to access")
        protected String storeName;
        
        @Option(name="--command", aliases="-c", 
                usage="If set, execute a command")
        protected String command;
        
        @Option(name="--debug", 
                usage="Show full error information")
        protected boolean debug;

        @Option(name="--authScheme", 
                usage="Scheme to use for authenticating to server")
        protected AuthScheme authScheme;

        @Option(name="--keyStorePath", 
                usage="Path to key store containing authentication credentials")
        protected String keyStorePath;    

        @Option(name="--keyStorePassword", 
                usage="Password for key store")
        protected String keyStorePassword;    
    }

    /**
     * @param args
     * @throws InterruptedException 
     */
    public static void main(String[] args) throws Exception {
        SyncClientSettings settings = new SyncClientSettings();
        CmdLineParser parser = new CmdLineParser(settings);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            System.exit(1);
        }
        if (settings.help) {
            parser.printUsage(System.err);
            System.exit(1);
        }
        
        SyncClient client = new SyncClient(settings);
        try {
            if (false == client.connect()) {
                return;
            }
            if (settings.command == null) {
                client.startShell(settings);
            } else {
                client.executeCommandLine(settings.command);
            }
        } finally {
            client.cleanup();
        }
    }
}
