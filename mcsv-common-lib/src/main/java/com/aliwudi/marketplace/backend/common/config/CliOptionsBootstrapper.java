package com.aliwudi.marketplace.backend.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
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
        gaurdRail();
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

    public static Object getStaticFieldValue(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true); // Allow access to private/protected fields

            if (Modifier.isStatic(field.getModifiers())) {
                return field.get(null); // null because it's a static field
            } else {
                System.out.println("Field '" + fieldName + "' is not static.");
            }
        } catch (NoSuchFieldException e) {
            System.out.println("Field '" + fieldName + "' does not exist.");
        } catch (IllegalAccessException e) {
            System.out.println("Cannot access field '" + fieldName + "'.");
        }

        return null;
    }

    private static Object getPropertyValue(String filePath, String key) throws IOException {
        InputStream input = clazzBooting.getClassLoader().getResourceAsStream(filePath);

        if (input == null) {
            return null;
        }

        Properties prop = new Properties();
        prop.load(input);
        return prop.getProperty(key);

    }

    public static Object getYamlValue(String filePath, String keyPath) throws IOException {
        InputStream input = clazzBooting.getClassLoader().getResourceAsStream(filePath);
        if (input == null) {
            return null;
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> yamlMap = mapper.readValue(input, Map.class);

        // Flatten the map with dot-separated keys
        Map<String, Object> flatMap = new LinkedHashMap<>();
        flattenMap("", yamlMap, flatMap);

        // Lookup full key
        return flatMap.get(keyPath);
    }

    private static void flattenMap(String prefix, Object value, Map<String, Object> result) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String newPrefix = prefix.isEmpty() ? entry.getKey().toString() : prefix + "." + entry.getKey();
                flattenMap(newPrefix, entry.getValue(), result);
            }
        } else {
            result.put(prefix, value);
        }
    }

    public static void gaurdRail() {
        String msg;
        String MUST_ROLE_ID_FV = "VAULT_ROLE_ID";
        String MUST_SECRET_ID_FV = "VAULT_SECRET_ID";
        if (!MUST_ROLE_ID_FV.equals(
                getStaticFieldValue(CliOptionsBootstrapper.class, MUST_ROLE_ID_FV))) {

            msg = msgCodeBreakFieldScensitive("role-id", MUST_ROLE_ID_FV);
            exitWithError(msg);
            
        } else if (!MUST_SECRET_ID_FV.equals(
                getStaticFieldValue(CliOptionsBootstrapper.class, MUST_SECRET_ID_FV))) {

            msg = msgCodeBreakFieldScensitive("secret-id", MUST_SECRET_ID_FV);
            exitWithError(msg);
        }

        String applicationFileYml = "application.yml";
        String applicationFileProp = "application.properties";

        String pathSecretId = "spring.cloud.vault.app-role.secret-id";
        String pathRoleId = "spring.cloud.vault.app-role.role-id";
        String VAULT_ROLE_ID_PARAM = "${" + VAULT_ROLE_ID + "}";
        String VAULT_SECRET_ID_PARAM = "${" + VAULT_SECRET_ID + "}";

        Object roleIdParam = null;
        Object secretIdParma = null;
        String errMsg1 = null;
        String errMsg2 = null;
        try {
            roleIdParam = getYamlValue(applicationFileYml, pathRoleId);
            secretIdParma = getYamlValue(applicationFileYml, pathSecretId);
        } catch (IOException ex) {
            errMsg1 = ex.getMessage();
        }
        try {
            if (roleIdParam == null) {
                roleIdParam = getPropertyValue(applicationFileProp, pathRoleId);
            }

            if (secretIdParma == null) {
                secretIdParma = getPropertyValue(applicationFileYml, pathSecretId);
            }
        } catch (IOException ex) {
            errMsg2 = ex.getMessage();
        }

        if (errMsg1 != null && errMsg2 != null) {
            exitWithError("Error checking onfiguration file - application.yml or application.properties");
            return;
        }

        if (!VAULT_ROLE_ID_PARAM.equals(roleIdParam)) {
            msg = msgCodeBreakVaultSensitive("role id", VAULT_ROLE_ID_PARAM, pathRoleId);
            exitWithError(msg);
        } else if (!VAULT_SECRET_ID_PARAM.equals(secretIdParma)) {
            msg = msgCodeBreakVaultSensitive("secret id", VAULT_SECRET_ID_PARAM, pathSecretId);
            exitWithError(msg);
        }

    }

    static private String msgCodeBreakVaultSensitive(String paramA, String paramB, String paramC) {
        String missing = paramC + ": " + paramB;
        String msg = """
                  Code break detection! Developer attention highly required.
                  Vault %s parameter "%s" expected but not found in application.yml
                  or application.propesties config file. 
                     
                  Missing property:
               
                  %s
                  
               """;
        return String.format(msg, paramA, paramB, missing);
    }

    static private String msgCodeBreakFieldScensitive(String paramA, String paramB) {
        String field_def = "private static final String "+paramB+" = \""+paramB+"\"";
        String msg = """
                  Code break detection! Developer attention highly required.
                  As a guard rail requirement to prevent bugs by future codebase
                  management we enforce certain field definitions. 
                  It is mandatory %s field and its mandatory value is explicitly defined in
                  %s class  as it is used to ensure vault %s parameter ${%s}
                  is set and assigned value in application.yml or application.propesties
                     
                  Define this field as so in %s class :
               
                  %s
                  
               """;
        return String.format(msg,
                paramB, 
                CliOptionsBootstrapper.class.getSimpleName(),
                paramA, 
                paramB,
                CliOptionsBootstrapper.class.getSimpleName(),
                field_def);
        
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
        System.exit(0);
    }

    public static void printUsage() {
        System.out.println("Usage: java -jar " + clazzBooting.getSimpleName() + ".jar [options]");
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
