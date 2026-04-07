package org.apache.camel.component.temporal.e2e.route;

import org.apache.camel.builder.RouteBuilder;

public class TemporalKameletRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        AppProperties properties = new AppProperties(getContext());
        String operation = properties.require("app.operation").toLowerCase(java.util.Locale.ROOT);

        switch (operation) {
            case "start" -> configureStart(properties);
            case "signal" -> configureSignal(properties);
            case "query" -> configureQuery(properties);
            default -> throw new IllegalArgumentException(
                "Unsupported app.operation '" + operation + "'. Expected: start, signal, query");
        }
    }

    private void configureStart(AppProperties properties) throws Exception {
        from("timer:start-once?repeatCount=1&delay=1000")
            .routeId("temporal-kamelet-start")
            .setBody(constant(properties.require("app.payload")))
            .to(startUri(properties))
            .log("TEMPORAL_START workflowId=${header.CamelTemporalWorkflowId} runId=${header.CamelTemporalWorkflowRunId}");
    }

    private void configureSignal(AppProperties properties) throws Exception {
        String workflowId = properties.require("app.workflowId");
        String signalName = properties.require("app.signalName");
        from("timer:signal-once?repeatCount=1&delay=1000")
            .routeId("temporal-kamelet-signal")
            .setBody(constant(properties.optional("app.signalPayload")))
            .to(signalUri(properties, workflowId, signalName))
            .log("TEMPORAL_SIGNAL workflowId=" + workflowId + " signal=" + signalName);
    }

    private void configureQuery(AppProperties properties) throws Exception {
        String workflowId = properties.require("app.workflowId");
        from("timer:query-once?repeatCount=1&delay=1000")
            .routeId("temporal-kamelet-query")
            .to(queryUri(properties, workflowId))
            .log("TEMPORAL_QUERY workflowId=" + workflowId + " status=${body}");
    }

    private String startUri(AppProperties properties) throws Exception {
        return baseUri("temporal-workflow-start-action", properties)
            .with("taskQueue", properties.require("app.taskQueue"))
            .with("workflowType", properties.require("app.workflowType"))
            .withIfPresent("workflowId", properties.optional("app.workflowId"))
            .with("workflowExecutionTimeoutSeconds", properties.require("app.workflowExecutionTimeoutSeconds"))
            .build();
    }

    private String signalUri(AppProperties properties, String workflowId, String signalName) throws Exception {
        return baseUri("temporal-workflow-signal-action", properties)
            .with("workflowId", workflowId)
            .with("signalName", signalName)
            .build();
    }

    private String queryUri(AppProperties properties, String workflowId) throws Exception {
        return baseUri("temporal-workflow-query-action", properties)
            .with("workflowId", workflowId)
            .with("queryType", properties.require("app.queryType"))
            .build();
    }

    private KameletUriBuilder baseUri(String action, AppProperties properties) throws Exception {
        return KameletUriBuilder.forAction(action)
            .withAll(properties.connectionParameters());
    }
}
