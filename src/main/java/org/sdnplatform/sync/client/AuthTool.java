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
        @Option(name="--help", 
                usage="Show help")
        protected boolean help;
        
        @Option(name="--keyStorePath", 
                usage="Path to JCEKS key store where credentials should " + 
                       "be stored",
                required=true)
        protected String keyStorePath;
        
        @Option(name="--keyStorePassword",
                usage="Password for key store")
        protected String keyStorePassword;    

        @Option(name="--authScheme",
                usage="Auth scheme for which we should set up credentials",
                required=true)
        protected AuthScheme authScheme;
    }
    
    public static void main(String[] args) throws Exception {
        AuthToolSettings settings = new AuthToolSettings();
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
        if (settings.keyStorePassword == null) {
            Console con = System.console();
            char[] password = con.readPassword("Enter key store password: ");
            settings.keyStorePassword = new String(password);
        }
        
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
