package com.aliwudi.marketplace.backend.common.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CliOptionsBootstrapper {

    private static Logger logger = LoggerFactory.getLogger(CliOptionsBootstrapper.class);
    private static String roleIdPath;
    private static String secretIdPath;
    private static boolean helpRequested = false;

    private static String ROOT;
    private static String SECRET_PATH;
    private static String DEFAULT_ROLE_ID_PATH;
    private static String DEFAULT_SECRET_ID_PATH;
    private static final String VAULT_ROLE_ID = "VAULT_ROLE_ID";
    private static final String VAULT_SECRET_ID = "VAULT_SECRET_ID";
    private static Class clazzBooting;
    static public void check(Class clazz, String[] args) {
        clazzBooting = clazz;
        logger = LoggerFactory.getLogger(clazz);
        ROOT = File.listRoots()[0].getAbsolutePath().replace('\\', '/');
        SECRET_PATH = ROOT + clazz.getSimpleName() + "/secret";
        DEFAULT_ROLE_ID_PATH = SECRET_PATH + "/role-id.txt";
        DEFAULT_SECRET_ID_PATH = SECRET_PATH + "/secret-id.txt";

        parse(args);
        if (helpRequested) {
            printUsage();
            System.exit(0);
        }

        String roleId_path = getInputOrEnvOrPrompt("Role ID", roleIdPath, "VAULT_ROLE_ID_PATH", DEFAULT_ROLE_ID_PATH);
        String secretId_path = getInputOrEnvOrPrompt("Secret ID", secretIdPath, "VAULT_SECRET_ID_PATH", DEFAULT_SECRET_ID_PATH);

        String roleId = readFile(roleId_path);
        String secretId = readFile(secretId_path);

        if (roleId == null || roleId.isEmpty()) {
            exitWithError("Error: Role ID file is empty or unreadable: " + roleId_path);
        }

        if (secretId == null || secretId.isEmpty()) {
            exitWithError("Error: Secret ID file is empty or unreadable: " + secretId_path);
        }

        System.setProperty(VAULT_ROLE_ID, roleId);
        System.setProperty(VAULT_SECRET_ID, secretId);
    }

    static private void parse(String[] args) {
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-r", "-role-id-path" -> {
                    if (i + 1 < args.length) {
                        flags.put("role-id-path", args[++i]);
                    } else {
                        error("Missing value for " + args[i]);
                    }
                }
                case "-s", "-secret-id-path" -> {
                    if (i + 1 < args.length) {
                        flags.put("secret-id-path", args[++i]);
                    } else {
                        error("Missing value for " + args[i]);
                    }
                }
                case "-h", "-help" -> {
                    helpRequested = true;
                    return;
                }
                default -> {
                    if (args[i].startsWith("-")) {
                        error("Unknown option: " + args[i]);
                    }
                }
            }
        }
        roleIdPath = flags.get("role-id-path");
        secretIdPath = flags.get("secret-id-path");
    }

    static private void error(String message) {
        System.err.println("Error: " + message);
        printUsage();
        System.exit(1);
    }

    private static String getInputOrEnvOrPrompt(String label, String cliValue, String envVar, String defaultPath) {
        if (cliValue != null) {
            return cliValue;
        }

        String fromEnv = System.getenv(envVar);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        return promptForPath(label, defaultPath);
    }

    private static String promptForPath(String label, String defaultPath) {
        System.out.printf("Enter path for %s file [%s]: ", label, defaultPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            String input = reader.readLine();
            return (input == null || input.trim().isEmpty()) ? defaultPath : input.trim();
        } catch (IOException e) {
            exitWithError("Failed to read input for " + label + ": " + e.getMessage());
            return null; // unreachable
        }
    }

    private static String readFile(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            logger.error("Failed to read file '{}': {}", path, e.getMessage());
            return null;
        }
    }

    private static void exitWithError(String msg) {
        System.err.println(msg);
        printUsage();
        System.exit(1);
    }

    public static void printUsage() {
        System.out.println("Usage: java -jar "+clazzBooting.getSimpleName()+".jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -r,  -role-id-path     Path to Vault role ID file (default: " + DEFAULT_ROLE_ID_PATH + ")");
        System.out.println("  -s,  -secret-id-path   Path to Vault secret ID file (default: " + DEFAULT_SECRET_ID_PATH + ")");
        System.out.println("  -h,  -help             Show this help message and exit");
        System.out.println();
        System.out.println("You can also use environment variables:");
        System.out.println("  VAULT_ROLE_ID_PATH and VAULT_SECRET_ID_PATH");
        System.out.println("If not provided, you'll be prompted interactively.");
    }

}
