package com.aliwudi.marketplace.backend.common.config;

import com.aliwudi.marketplace.backend.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CliOptionsBootstrapper {

    private static Logger logger = LoggerFactory.getLogger(CliOptionsBootstrapper.class);
    private static String roleIdPath;
    private static String secretIdPath;
    private static String rootPath;
    private static boolean helpRequested = false;
    private static boolean nonInteractive = false;
    private static boolean strictSecurity = false; // New field for strict security mode

    private static String ROOT;
    private static String SECRET_PATH;
    private static String DEFAULT_ROLE_ID_PATH;
    private static String DEFAULT_SECRET_ID_PATH;
    private static final String ROOT_PATH_ENV = "ROOT_PATH";
    private static final String STRICT_SECURITY_ENV = "STRICT_SECURITY"; // New environment variable
    private static final String VAULT_ROLE_ID = "VAULT_ROLE_ID";
    private static final String VAULT_SECRET_ID = "VAULT_SECRET_ID";
    private static Class clazzBooting;

    /**
     * Initializes the CLI configuration for the specified class with the given arguments.
     * Configures the root path, parses CLI arguments, and sets Vault credentials as system properties.
     *
     * @param clazz The class being bootstrapped.
     * @param args  Command-line arguments.
     */
    static public void check(Class clazz, String[] args) {
        clazzBooting = clazz;
        gaurdRail();
        logger = LoggerFactory.getLogger(clazz);

        // Set ROOT path: CLI > Environment Variable > Working Directory
        ROOT = getRootPath(args);
        ROOT = ROOT.replace('\\', '/');
        if (!ROOT.endsWith("/")) {
            ROOT += "/";
        }

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

    /**
     * Determines the root path from CLI arguments, environment variables, or the working directory.
     *
     * @param args Command-line arguments to parse for root path.
     * @return The resolved root path.
     */
    private static String getRootPath(String[] args) {
        // Check CLI arguments first (parsed in parse method)
        parse(args); // Ensure rootPath is populated
        if (rootPath != null && !rootPath.isBlank()) {
            logger.debug("Using root path from CLI argument: {}", rootPath);
            File rootDir = new File(rootPath);
            if (!rootDir.exists() || !rootDir.isDirectory()) {
                logger.warn("Root path '{}' does not exist or is not a directory. Falling back to working directory.", rootPath);
                return System.getProperty("user.dir");
            }
            return rootPath;
        }

        // Check environment variable
        String envRootPath = System.getenv(ROOT_PATH_ENV);
        if (envRootPath != null && !envRootPath.isBlank()) {
            logger.debug("Using root path from environment variable {}: {}", ROOT_PATH_ENV, envRootPath);
            File rootDir = new File(envRootPath);
            if (!rootDir.exists() || !rootDir.isDirectory()) {
                logger.warn("Root path '{}' from {} does not exist or is not a directory. Falling back to working directory.", envRootPath, ROOT_PATH_ENV);
                return System.getProperty("user.dir");
            }
            return envRootPath;
        }

        // Fallback to working directory
        String workingDir = System.getProperty("user.dir");
        logger.debug("Using default root path (working directory): {}", workingDir);
        return workingDir;
    }

    /**
     * Retrieves the input path from CLI, environment variable, or prompt.
     * In non-interactive mode, fails if no input is provided instead of prompting.
     *
     * @param label       The label for the input (e.g., "Role ID").
     * @param cliValue    The value from CLI arguments.
     * @param envVar      The environment variable name.
     * @param defaultPath The default path to use.
     * @return The resolved path.
     */
    private static String getInputOrEnvOrPrompt(String label, String cliValue, String envVar, String defaultPath) {
        if (cliValue != null && !cliValue.isBlank()) {
            logger.debug("Using {} path from CLI: {}", label, cliValue);
            return cliValue;
        }

        String fromEnv = System.getenv(envVar);
        if (fromEnv != null && !fromEnv.isBlank()) {
            logger.debug("Using {} path from environment variable {}: {}", label, envVar, fromEnv);
            return fromEnv;
        }

        if (nonInteractive) {
            logger.error("No {} path provided via CLI or environment variable {} in non-interactive mode", label, envVar);
            exitWithError(String.format("Error: %s path must be provided via CLI (-r/-s) or environment variable (%s) in non-interactive mode", label, envVar));
            return null; // Unreachable due to exit
        }

        return promptForPath(label, defaultPath);
    }

    /**
     * Prompts the user for a file path interactively.
     *
     * @param label       The label for the input (e.g., "Role ID").
     * @param defaultPath The default path to suggest.
     * @return The user-provided or default path.
     */
    private static String promptForPath(String label, String defaultPath) {
        System.out.printf("Enter path for %s file [%s]: ", label, defaultPath);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            String input = reader.readLine();
            return (input == null || input.trim().isEmpty()) ? defaultPath : input.trim();
        } catch (IOException e) {
            exitWithError("Failed to read input for " + label + ": " + e.getMessage());
            return null; // Unreachable due to exit
        }
    }

    /**
     * Reads the contents of a file into a string, checking for secure permissions.
     *
     * @param path The file path to read.
     * @return The file contents, or null if an error occurs or permissions are insecure in strict mode.
     */
    private static String readFile(String path) {
        Path filePath = Path.of(path);
        try {
            // Check file permissions
            boolean isInsecure = false;
            StringBuilder permissionWarning = new StringBuilder();

            // Cross-platform permission checks
            File file = filePath.toFile();
            if (file.exists()) {
                // Check if world-readable or world-writable (basic check for non-POSIX systems)
                if (file.canRead() && file.canWrite()) { // Note: canRead/canWrite are limited, but check for excessive permissions
                    permissionWarning.append("File '").append(path).append("' is readable and writable by the application, which may indicate overly permissive settings. ");
                    isInsecure = true;
                }

                // POSIX-specific permission checks (Unix-like systems)
                try {
                    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(filePath);
                    if (perms.contains(PosixFilePermission.OTHERS_READ)) {
                        permissionWarning.append("File '").append(path).append("' is world-readable. ");
                        isInsecure = true;
                    }
                    if (perms.contains(PosixFilePermission.OTHERS_WRITE)) {
                        permissionWarning.append("File '").append(path).append("' is world-writable. ");
                        isInsecure = true;
                    }
                    // Ensure owner has read permissions
                    if (!perms.contains(PosixFilePermission.OWNER_READ)) {
                        permissionWarning.append("File '").append(path).append("' is not readable by the owner. ");
                        isInsecure = true;
                    }
                } catch (UnsupportedOperationException e) {
                    logger.debug("POSIX file permissions not supported on this system for '{}'. Falling back to basic checks.", path);
                }

                if (isInsecure) {
                    String recommendation = "Ensure the file has restricted permissions (e.g., chmod 600 on Unix-like systems) to limit access to the owner only.";
                    if (strictSecurity) {
                        logger.error("Insecure file permissions detected: {}. {}", permissionWarning, recommendation);
                        exitWithError(String.format("Error: Insecure file permissions for '%s'. %s", path, recommendation));
                        return null; // Unreachable due to exit
                    } else {
                        logger.warn("Insecure file permissions detected: {}. {}", permissionWarning, recommendation);
                    }
                }
            } else {
                logger.error("File '{}' does not exist.", path);
                return null;
            }

            // Read the file
            return Files.readString(filePath, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            logger.error("Failed to read file '{}': {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Parses command-line arguments to extract role-id-path, secret-id-path, root-path, non-interactive, and strict-security flags.
     *
     * @param args Command-line arguments.
     */
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
                case "-p", "-root-path" -> {
                    if (i + 1 < args.length) {
                        flags.put("root-path", args[++i]);
                    } else {
                        error("Missing value for " + args[i]);
                    }
                }
                case "-n", "-non-interactive" -> {
                    nonInteractive = true;
                }
                case "-strict-security" -> { // New CLI option for strict security
                    strictSecurity = true;
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
        rootPath = flags.get("root-path");

        // Check environment variable for strict security if not set via CLI
        if (!strictSecurity) {
            String envStrictSecurity = System.getenv(STRICT_SECURITY_ENV);
            strictSecurity = "true".equalsIgnoreCase(envStrictSecurity);
        }
    }

    /**
     * Prints usage information for the CLI, including root-path, non-interactive, and strict-security options.
     */
    public static void printUsage() {
        System.out.println("Usage: java -jar " + (clazzBooting != null ? clazzBooting.getSimpleName() : "Application") + ".jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -r,  -role-id-path     Path to Vault role ID file (default: " + DEFAULT_ROLE_ID_PATH + ")");
        System.out.println("  -s,  -secret-id-path   Path to Vault secret ID file (default: " + DEFAULT_SECRET_ID_PATH + ")");
        System.out.println("  -p,  -root-path        Root path for secret files (default: working directory)");
        System.out.println("  -n,  -non-interactive  Disable interactive prompts and fail if inputs are missing");
        System.out.println("  -strict-security       Fail if file permissions are insecure (e.g., world-readable)");
        System.out.println("  -h,  -help             Show this help message and exit");
        System.out.println();
        System.out.println("You can also use environment variables:");
        System.out.println("  ROOT_PATH, VAULT_ROLE_ID_PATH, VAULT_SECRET_ID_PATH, STRICT_SECURITY");
        System.out.println("In interactive mode, you'll be prompted for role-id and secret-id paths if not provided.");
    }

    /**
     * Exits with an error message and non-zero status code.
     *
     * @param msg The error message to display.
     */
    private static void exitWithError(String msg) {
        System.err.println(msg);
        logger.error(msg);
        printUsage();
        System.exit(1);
    }

    /**
     * Logs an error message and exits with usage information.
     *
     * @param message The error message to display.
     */
    static private void error(String message) {
        System.err.println("Error: " + message);
        logger.error(message);
        printUsage();
        System.exit(1);
    }

    /**
     * Retrieves a static field value from a class using reflection.
     *
     * @param clazz     The class containing the field.
     * @param fieldName The name of the field.
     * @return The field value, or null if an error occurs.
     */
    public static Object getStaticFieldValue(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            if (Modifier.isStatic(field.getModifiers())) {
                return field.get(null);
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

    /**
     * Retrieves a property value from a properties file.
     *
     * @param filePath The path to the properties file.
     * @param key      The property key to retrieve.
     * @return The property value, or null if not found.
     * @throws IOException If an I/O error occurs.
     * @throws ResourceNotFoundException If the file is not found.
     */
    private static Object getPropertyValue(String filePath, String key) throws IOException, ResourceNotFoundException {
        try (InputStream input = clazzBooting.getClassLoader().getResourceAsStream(filePath)) {
            if (input == null) {
                throw new ResourceNotFoundException("Properties file path not found");
            }
            Properties prop = new Properties();
            prop.load(input);
            return prop.getProperty(key);
        }
    }

    /**
     * Retrieves a value from a YAML file using a dot-separated key path.
     *
     * @param filePath The path to the YAML file.
     * @param keyPath  The dot-separated key path (e.g., "spring.cloud.vault").
     * @return The value, or null if not found.
     * @throws IOException If an I/O error occurs.
     * @throws ResourceNotFoundException If the file is not found.
     */
    public static Object getYamlValue(String filePath, String keyPath) throws IOException, ResourceNotFoundException {
        try (InputStream input = clazzBooting.getClassLoader().getResourceAsStream(filePath)) {
            if (input == null) {
                throw new ResourceNotFoundException("Yaml file path not found");
            }
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> yamlMap = mapper.readValue(input, Map.class);
            Map<String, Object> flatMap = new LinkedHashMap<>();
            flattenMap("", yamlMap, flatMap);
            return flatMap.get(keyPath);
        }
    }

    /**
     * Flattens a nested map or list into a flat map with dot-separated keys.
     * Supports nested maps, lists, and non-string keys.
     *
     * @param prefix The current key prefix (e.g., "spring.cloud").
     * @param value  The object to flatten (map, list, or primitive).
     * @param result The flat map to store key-value pairs.
     */
    private static void flattenMap(String prefix, Object value, Map<String, Object> result) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String newPrefix = prefix.isEmpty() ? key : prefix + "." + key;
                flattenMap(newPrefix, entry.getValue(), result);
            }
        } else if (value instanceof Iterable) {
            Iterable<?> iterable = (Iterable<?>) value;
            int index = 0;
            for (Object element : iterable) {
                String newPrefix = prefix + "[" + index + "]";
                flattenMap(newPrefix, element, result);
                index++;
            }
        } else {
            result.put(prefix, value);
        }
    }

    /**
     * Enforces guardrails to prevent configuration errors.
     */
    public static void gaurdRail() {
        String msg;
        String MUST_ROLE_ID_FV = "VAULT_ROLE_ID";
        String MUST_SECRET_ID_FV = "VAULT_SECRET_ID";
        if (!MUST_ROLE_ID_FV.equals(getStaticFieldValue(CliOptionsBootstrapper.class, MUST_ROLE_ID_FV))) {
            msg = msgCodeBreakFieldScensitive("role-id", MUST_ROLE_ID_FV);
            exitWithError(msg);
        } else if (!MUST_SECRET_ID_FV.equals(getStaticFieldValue(CliOptionsBootstrapper.class, MUST_SECRET_ID_FV))) {
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
        } catch (IOException | ResourceNotFoundException ex) {
            errMsg1 = ex.getMessage();
        }
        try {
            if (roleIdParam == null) {
                roleIdParam = getPropertyValue(applicationFileProp, pathRoleId);
            }
            if (secretIdParma == null) {
                secretIdParma = getPropertyValue(applicationFileProp, pathSecretId);
            }
        } catch (IOException | ResourceNotFoundException ex) {
            errMsg2 = ex.getMessage();
        }

        if (errMsg1 != null && errMsg2 != null) {
            exitWithError("Error checking configuration file - application.yml or application.properties\n" + errMsg1 + "\n" + errMsg2);
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

    /**
     * Creates an error message for a vault-sensitive configuration issue.
     *
     * @param paramA The parameter name (e.g., "role id").
     * @param paramB The expected value.
     * @param paramC The configuration path.
     * @return The formatted error message.
     */
    static private String msgCodeBreakVaultSensitive(String paramA, String paramB, String paramC) {
        String missing = paramC + ": " + paramB;
        String msg = """
                  Code break detection! Developer attention highly required.
                  Vault %s parameter "%s" expected but not found in application.yml
                  or application.properties config file.

                  Missing property:

                  %s
                  """;
        return String.format(msg, paramA, paramB, missing);
    }

    /**
     * Creates an error message for a field-sensitive configuration issue.
     *
     * @param paramA The parameter name (e.g., "role-id").
     * @param paramB The expected field name.
     * @return The formatted error message.
     */
    static private String msgCodeBreakFieldScensitive(String paramA, String paramB) {
        String field_def = "private static final String " + paramB + " = \"" + paramB + "\"";
        String msg = """
                  Code break detection! Developer attention highly required.
                  As a guard rail requirement to prevent bugs by future codebase
                  management we enforce certain field definitions.
                  It is mandatory %s field and its mandatory value is explicitly defined in
                  %s class as it is used to ensure vault %s parameter ${%s}
                  is set and assigned value in application.yml or application.properties

                  Define this field as so in %s class:

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
}