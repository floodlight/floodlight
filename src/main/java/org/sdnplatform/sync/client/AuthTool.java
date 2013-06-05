package org.sdnplatform.sync.client;

import java.io.Console;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.sdnplatform.sync.internal.config.AuthScheme;
import org.sdnplatform.sync.internal.util.CryptoUtil;

/**
 * Command-line tool for setting up authentication credentials
 * @author readams
 */
public class AuthTool {
    protected static class AuthToolSettings {
        @Option(name="--help", aliases="-h",
                usage="Show help")
        protected boolean help;
        
        @Option(name="--keyStorePath", aliases="-ks",
                usage="Path to JCEKS key store where credentials should " + 
                       "be stored")
        protected String keyStorePath;
        
        @Option(name="--keyStorePassword", aliases="-kp",
                usage="Password for key store")
        protected String keyStorePassword;    

        @Option(name="--authScheme", aliases="-a",
                usage="Auth scheme for which we should set up credentials " +
                      "(default NO_AUTH)")
        protected AuthScheme authScheme = AuthScheme.NO_AUTH;

        CmdLineParser parser = new CmdLineParser(this);

        protected void init(String[] args) {
            try {
                parser.parseArgument(args);
            } catch (CmdLineException e) {
                System.err.println(e.getMessage());
                parser.printUsage(System.err);
                System.exit(1);
            }
            if (help) {
                parser.printUsage(System.err);
                System.exit(1);
            }
            if (!AuthScheme.NO_AUTH.equals(authScheme)) {
                if (keyStorePath == null) {
                    System.err.println("keyStorePath is required when " + 
                                       "authScheme is " + authScheme);
                    parser.printUsage(System.err);
                    System.exit(1);
                }
                if (keyStorePassword == null) {
                    Console con = System.console();
                    char[] password = con.readPassword("Enter key store password: ");
                    keyStorePassword = new String(password);
                }
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        AuthToolSettings settings = new AuthToolSettings();
        settings.init(args);        
        
        switch (settings.authScheme) {
            case NO_AUTH:
                System.err.println("No credentials required for NO_AUTH");
                break;
            case CHALLENGE_RESPONSE:
                CryptoUtil.writeSharedSecret(settings.keyStorePath, 
                                             settings.keyStorePassword, 
                                             CryptoUtil.secureRandom(16));
                System.out.println("Wrote random 128-bit secret to " +
                                   settings.keyStorePath);
                break;
        }        
    }
}
