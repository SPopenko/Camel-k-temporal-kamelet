package org.apache.camel.component.temporal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Temporal producer. Dispatches to start, signal, or query operations on Temporal workflows.
 *
 * Operation dispatch is based on the URI path (e.g., temporal://start, temporal://signal).
 * Configuration can be overridden per-message via Exchange headers (see TemporalConstants).
 */
public class TemporalProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TemporalProducer.class);

    private final TemporalEndpoint endpoint;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TemporalProducer(TemporalEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = endpoint.getOperation();
        switch (operation) {
            case "start"  -> processStart(exchange);
            case "signal" -> processSignal(exchange);
            case "query"  -> processQuery(exchange);
            default -> throw new IllegalArgumentException(
                "Unknown Temporal operation: '" + operation +
                "'. Supported: start, signal, query");
        }
    }

    private void processStart(Exchange exchange) throws Exception {
        TemporalConfiguration config = endpoint.getConfiguration();
        WorkflowClient client = endpoint.getWorkflowClient();

        // Resolve parameters: header > config > default
        String workflowId = getHeader(exchange, TemporalConstants.TEMPORAL_WORKFLOW_ID,
                                       config.getWorkflowId());
        if (workflowId == null || workflowId.isBlank()) {
            workflowId = UUID.randomUUID().toString();
        }

        String workflowType = getHeader(exchange, TemporalConstants.TEMPORAL_WORKFLOW_TYPE,
                                         config.getWorkflowType());
        if (workflowType == null || workflowType.isBlank()) {
            throw new IllegalArgumentException("workflowType is required for start operation");
        }

        String taskQueue = getHeader(exchange, TemporalConstants.TEMPORAL_TASK_QUEUE,
                                      config.getTaskQueue());
        if (taskQueue == null || taskQueue.isBlank()) {
            throw new IllegalArgumentException("taskQueue is required for start operation");
        }

        WorkflowOptions.Builder optionsBuilder = WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue(taskQueue);

        if (config.getWorkflowExecutionTimeoutSeconds() > 0) {
            optionsBuilder.setWorkflowExecutionTimeout(
                Duration.ofSeconds(config.getWorkflowExecutionTimeoutSeconds()));
        }

        WorkflowStub stub = client.newUntypedWorkflowStub(workflowType, optionsBuilder.build());

        Object body = exchange.getIn().getBody();
        WorkflowExecution execution;
        if (body != null) {
            String payload = toJsonString(body);
            execution = stub.start(payload);
        } else {
            execution = stub.start();
        }

        LOG.info("Started workflow: type={}, workflowId={}, runId={}",
                 workflowType, execution.getWorkflowId(), execution.getRunId());

        // Set result headers
        exchange.getIn().setHeader(TemporalConstants.TEMPORAL_WORKFLOW_ID,
                                    execution.getWorkflowId());
        exchange.getIn().setHeader(TemporalConstants.TEMPORAL_WORKFLOW_RUN_ID,
                                    execution.getRunId());
        // Return workflowId as body for easy downstream chaining
        exchange.getMessage().setBody(execution.getWorkflowId());
    }

    private void processSignal(Exchange exchange) throws Exception {
        TemporalConfiguration config = endpoint.getConfiguration();
        WorkflowClient client = endpoint.getWorkflowClient();

        String workflowId = requireParam(exchange, TemporalConstants.TEMPORAL_WORKFLOW_ID,
                                          config.getWorkflowId(), "workflowId", "signal");
        String signalName = requireParam(exchange, TemporalConstants.TEMPORAL_SIGNAL_NAME,
                                          config.getSignalName(), "signalName", "signal");

        WorkflowStub stub = client.newUntypedWorkflowStub(workflowId,
            Optional.empty(), Optional.empty());

        Object body = exchange.getIn().getBody();
        if (body != null) {
            String payload = toJsonString(body);
            stub.signal(signalName, payload);
        } else {
            stub.signal(signalName);
        }

        LOG.info("Sent signal '{}' to workflowId={}", signalName, workflowId);
        exchange.getMessage().setBody(null);
    }

    private void processQuery(Exchange exchange) throws Exception {
        TemporalConfiguration config = endpoint.getConfiguration();
        WorkflowClient client = endpoint.getWorkflowClient();

        String workflowId = requireParam(exchange, TemporalConstants.TEMPORAL_WORKFLOW_ID,
                                          config.getWorkflowId(), "workflowId", "query");
        String queryType = requireParam(exchange, TemporalConstants.TEMPORAL_QUERY_TYPE,
                                         config.getQueryType(), "queryType", "query");

        WorkflowStub stub = client.newUntypedWorkflowStub(workflowId,
            Optional.empty(), Optional.empty());

        String result = stub.query(queryType, String.class);

        LOG.info("Query '{}' on workflowId={} returned: {}", queryType, workflowId, result);
        exchange.getMessage().setBody(result);
        exchange.getIn().setHeader(TemporalConstants.TEMPORAL_WORKFLOW_RESULT, result);
    }

    private String getHeader(Exchange exchange, String headerName, String fallback) {
        String value = exchange.getIn().getHeader(headerName, String.class);
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private String requireParam(Exchange exchange, String headerName, String configValue,
                                 String paramName, String operation) {
        String value = getHeader(exchange, headerName, configValue);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "Parameter '" + paramName + "' is required for operation: " + operation);
        }
        return value;
    }

    private String toJsonString(Object body) throws Exception {
        if (body instanceof String s) {
            return s;
        }
        return objectMapper.writeValueAsString(body);
    }
}
