package com.telcobright.party.api.config;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Loads Party runtime configuration from a per-(operator, profile) YAML file.
 *
 * <p>Party runs per operator (BTCL, etc.); each operator's deployment in turn
 * manages many Tenants (a domain entity below operator in the graph
 * operator → tenant → partner → user). This source resolves which operator
 * + which environment profile this JVM should bootstrap as.
 *
 * <p>Resolution order for (operator, profile):
 * <ol>
 *   <li>System properties {@code party.operator.name} / {@code party.operator.profile}</li>
 *   <li>Env vars {@code PARTY_OPERATOR_NAME} / {@code PARTY_OPERATOR_PROFILE}</li>
 *   <li>First enabled entry under {@code party.operators[N].*} in application.properties</li>
 * </ol>
 *
 * <p>YAML lookup: {@code config/operators/<operator>/<profile>/profile-<profile>.yml} —
 * first tried as a classpath resource (packaged JAR), then as a filesystem path relative
 * to CWD (useful when running exploded for ops edits without a rebuild).
 *
 * <p>If the YAML is absent the source is a no-op. That is deliberate: the test profile and
 * {@code mvn quarkus:dev} rely on Quarkus Dev Services, which must not be overridden.
 */
public class PartyProfileConfigSource implements ConfigSource {

    private static final int ORDINAL = 270;
    private static final String NAME = "PartyProfileConfigSource";

    private final Map<String, String> properties = new HashMap<>();

    public PartyProfileConfigSource() {
        load();
    }

    private void load() {
        try {
            String operator = resolve("party.operator.name", "PARTY_OPERATOR_NAME");
            String profile  = resolve("party.operator.profile", "PARTY_OPERATOR_PROFILE");

            if (operator == null || profile == null) {
                Properties app = readAppProperties();
                if (operator == null) operator = firstEnabledOperatorField(app, "name");
                if (profile  == null) profile  = firstEnabledOperatorField(app, "profile");
            }

            if (operator == null || profile == null) {
                // Nothing configured yet — e.g. unit tests. Stay silent and yield to Dev Services.
                return;
            }

            properties.put("party.operator.name", operator);
            properties.put("party.operator.profile", profile);

            String rel = "config/operators/" + operator + "/" + profile + "/profile-" + profile + ".yml";
            Map<String, Object> yamlData = loadYaml(rel);
            if (yamlData == null) {
                System.err.println("[" + NAME + "] profile file not found on classpath or filesystem: " + rel
                        + " — leaving config untouched (Dev Services / defaults apply)");
                return;
            }

            flatten("", yamlData, properties);
            System.out.println("[" + NAME + "] loaded " + properties.size()
                    + " keys for operator=" + operator + " profile=" + profile);
        } catch (Exception e) {
            System.err.println("[" + NAME + "] error during init: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String resolve(String sysProp, String envVar) {
        String v = System.getProperty(sysProp);
        if (v == null || v.isBlank()) v = System.getenv(envVar);
        return (v == null || v.isBlank()) ? null : v;
    }

    private Properties readAppProperties() {
        Properties p = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) p.load(is);
        } catch (Exception ignored) {
            // best-effort; missing file is non-fatal
        }
        return p;
    }

    private String firstEnabledOperatorField(Properties app, String field) {
        for (int i = 0; i < 20; i++) {
            String name    = app.getProperty("party.operators[" + i + "].name");
            String enabled = app.getProperty("party.operators[" + i + "].enabled");
            if (name != null && "true".equalsIgnoreCase(enabled)) {
                return app.getProperty("party.operators[" + i + "]." + field);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(String relativePath) {
        // 1) classpath (packaged JAR)
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(relativePath)) {
            if (is != null) return (Map<String, Object>) new Yaml().load(is);
        } catch (Exception e) {
            System.err.println("[" + NAME + "] classpath YAML read failed: " + e.getMessage());
        }
        // 2) filesystem fallback — useful for running unpacked / ops-edits-without-rebuild
        try {
            Path p = Path.of(relativePath);
            if (Files.isReadable(p)) {
                try (InputStream is = Files.newInputStream(p)) {
                    return (Map<String, Object>) new Yaml().load(is);
                }
            }
            // also try under src/main/resources (dev-mode runs with CWD at the module root)
            Path src = Path.of("src", "main", "resources", relativePath);
            if (Files.isReadable(src)) {
                try (InputStream is = Files.newInputStream(src)) {
                    return (Map<String, Object>) new Yaml().load(is);
                }
            }
        } catch (Exception e) {
            System.err.println("[" + NAME + "] filesystem YAML read failed: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> map, Map<String, String> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map) {
                flatten(key, (Map<String, Object>) v, out);
            } else if (v instanceof List<?> list) {
                // Join scalar lists with commas; keep complex lists as toString (rare in our configs).
                boolean scalar = list.stream().allMatch(x -> x == null || x instanceof String || x instanceof Number || x instanceof Boolean);
                if (scalar) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(',');
                        Object x = list.get(i);
                        sb.append(x == null ? "" : x.toString());
                    }
                    out.put(key, sb.toString());
                } else {
                    out.put(key, list.toString());
                }
            } else {
                out.put(key, v == null ? "" : v.toString());
            }
        }
    }

    @Override public Map<String, String> getProperties() { return new HashMap<>(properties); }
    @Override public Set<String> getPropertyNames()       { return properties.keySet(); }
    @Override public String getValue(String name)         { return properties.get(name); }
    @Override public String getName()                     { return NAME; }
    @Override public int getOrdinal()                     { return ORDINAL; }
}
