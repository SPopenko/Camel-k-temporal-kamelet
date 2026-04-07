package org.apache.camel.component.temporal.e2e.route;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.camel.CamelContext;

final class AppProperties {

    private final CamelContext camelContext;

    AppProperties(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    String require(String key) throws Exception {
        String value = optional(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value;
    }

    String optional(String key) throws Exception {
        String environmentValue = System.getenv(toEnvironmentKey(key));
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        String resolved = camelContext.resolvePropertyPlaceholders("{{" + key + "}}");
        return resolved == null || resolved.isBlank() ? null : resolved;
    }

    Map<String, String> connectionParameters() throws Exception {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("host", require("app.host"));
        parameters.put("port", require("app.port"));
        parameters.put("namespace", require("app.namespace"));
        return parameters;
    }

    private String toEnvironmentKey(String key) {
        String normalized = key
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replace('.', '_')
            .replace('-', '_');
        return normalized.toUpperCase(Locale.ROOT);
    }
}
