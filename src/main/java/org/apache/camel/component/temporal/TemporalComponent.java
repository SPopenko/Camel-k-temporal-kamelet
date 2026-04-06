package org.apache.camel.component.temporal;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

import java.util.Map;

/**
 * Apache Camel component for Temporal.io workflow engine.
 *
 * URI format: temporal://start|signal|query?host=...&port=...&namespace=...
 */
public class TemporalComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
        String operation = remaining;
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException(
                "Operation is required in Temporal URI. Use: temporal://start|signal|query");
        }

        TemporalConfiguration configuration = new TemporalConfiguration();
        TemporalEndpoint endpoint = new TemporalEndpoint(uri, this, operation, configuration);
        setProperties(endpoint.getConfiguration(), parameters);
        return endpoint;
    }
}
