package org.sdnplatform.sync.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonParser;

import org.kohsuke.args4j.Option;
import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.ISyncService.Scope;
import org.sdnplatform.sync.error.UnknownStoreException;

/**
 * A command-line tool for interacting with a remote sync service 
 * @author readams
 */
public class SyncClient extends SyncClientBase {
    /**
     * Shell commands
     */
    protected HashMap<String, ShellCommand> commands;

    /**
     * Command-line settings
     */
    protected SyncClientSettings syncClientSettings;
    
    /**
     * Store client for currently-active store
     */
    protected IStoreClient<JsonNode, JsonNode> storeClient;

    protected static class SyncClientSettings 
        extends SyncClientBaseSettings {
    
        @Option(name="--store", aliases="-s", 
                usage="Store name to access")
        protected String storeName;

        @Option(name="--command", aliases="-c", 
                usage="If set, execute a command")
        protected String command;

        @Option(name="--debug", 
                usage="Show full error information")
        protected boolean debug;
    }

    public SyncClient(SyncClientSettings syncClientSettings) {
        super(syncClientSettings);
        this.syncClientSettings = syncClientSettings;
        
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

    // **************
    // SyncClientBase
    // **************

    @Override
    protected void connect() throws Exception {
        super.connect();

        if (syncClientSettings.storeName != null)
            getStoreClient();
    }

    // **************
    // Shell commands
    // **************
    
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
            JsonParser jp = mjf.createParser(sr);
                     
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
            n.putPOJO("key", keyNode);
            n.putPOJO("value", value.getValue());
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
            JsonParser jp = mjf.createParser(sr);
            
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
            JsonParser jp = mjf.createParser(sr);
                     
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
            
            syncClientSettings.storeName = tokens[1];
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

            syncClientSettings.storeName = tokens[1];
            syncManager.registerStore(syncClientSettings.storeName, scope);
            getStoreClient();
            return false;
        }

        @Override
        public String syntaxString() {
            return "register [storeName] [local|global]";
        }
        
    }

    // *************
    // Local methods 
    // *************

    protected boolean checkStoreSettings() {
        if (syncClientSettings.storeName == null) {
            err.println("No store selected.  Select using \"store\" command.");
            return false;
        }
        return true;
    }

    protected void getStoreClient() throws UnknownStoreException {
        storeClient = syncManager.getStoreClient(syncClientSettings.storeName, 
                                                 JsonNode.class, 
                                                 JsonNode.class);
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
                    if (syncClientSettings.debug)
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

    protected void startShell(SyncClientBaseSettings settings) 
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
    
    // ****
    // Main 
    // ****

    public static void main(String[] args) throws Exception {
        SyncClientSettings settings = new SyncClientSettings();
        settings.init(args);
        
        SyncClient client = new SyncClient(settings);
        try {
            client.connect();
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
