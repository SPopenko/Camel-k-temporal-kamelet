package org.apache.camel.component.temporal.e2e.route;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

final class KameletUriBuilder {

    private final String action;
    private final Map<String, String> parameters = new LinkedHashMap<>();

    private KameletUriBuilder(String action) {
        this.action = action;
    }

    static KameletUriBuilder forAction(String action) {
        return new KameletUriBuilder(action);
    }

    KameletUriBuilder with(String key, String value) {
        parameters.put(key, value);
        return this;
    }

    KameletUriBuilder withAll(Map<String, String> values) {
        parameters.putAll(values);
        return this;
    }

    KameletUriBuilder withIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            parameters.put(key, value);
        }
        return this;
    }

    String build() {
        if (parameters.isEmpty()) {
            return "kamelet:" + action;
        }

        StringJoiner joiner = new StringJoiner("&", "kamelet:" + action + "?", "");
        parameters.forEach((key, value) -> joiner.add(key + "=" + value));
        return joiner.toString();
    }
}
