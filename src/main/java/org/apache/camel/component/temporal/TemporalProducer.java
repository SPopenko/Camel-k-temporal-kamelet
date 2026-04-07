package org.apache.camel.component.temporal;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowStub;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporal producer. Dispatches to start, signal, or query operations on Temporal workflows.
 *
 * Operation dispatch is based on the URI path (e.g., temporal:start, temporal:signal).
 * Configuration can be overridden per-message via Exchange headers (see TemporalConstants).
 *
 * Note: the query operation returns the workflow's query result as a String. Query handler
 * implementations must return a type that Temporal's data converter serializes to a JSON string
 * (or a plain String). Complex return types are not currently supported.
 */
public class TemporalProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TemporalProducer.class);

    private final TemporalEndpoint endpoint;

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
        TemporalRequestContext request = new TemporalRequestContext(endpoint, exchange);
        String workflowId = request.startWorkflowId();
        String workflowType = request.requiredWorkflowType();
        String taskQueue = request.requiredTaskQueue();
        WorkflowStub stub = request.newStartWorkflowStub(workflowType, workflowId, taskQueue);

        String payload = request.serializedBody();
        WorkflowExecution execution;
        if (payload != null) {
            execution = stub.start(payload);
        } else {
            execution = stub.start();
        }

        LOG.info("Started workflow: type={}, workflowId={}, runId={}",
                 workflowType, execution.getWorkflowId(), execution.getRunId());

        // Use getMessage() consistently for all output so headers and body are on the same message
        exchange.getMessage().setHeader(TemporalConstants.TEMPORAL_WORKFLOW_ID,
                                         execution.getWorkflowId());
        exchange.getMessage().setHeader(TemporalConstants.TEMPORAL_WORKFLOW_RUN_ID,
                                         execution.getRunId());
        exchange.getMessage().setBody(execution.getWorkflowId());
    }

    private void processSignal(Exchange exchange) throws Exception {
        TemporalRequestContext request = new TemporalRequestContext(endpoint, exchange);
        String workflowId = request.requiredWorkflowId("signal");
        String signalName = request.requiredSignalName();
        WorkflowStub stub = request.newWorkflowStub(workflowId);

        String payload = request.serializedBody();
        if (payload != null) {
            stub.signal(signalName, payload);
        } else {
            stub.signal(signalName);
        }

        LOG.info("Sent signal '{}' to workflowId={}", signalName, workflowId);
        exchange.getMessage().setBody(null);
    }

    private void processQuery(Exchange exchange) throws Exception {
        TemporalRequestContext request = new TemporalRequestContext(endpoint, exchange);
        String workflowId = request.requiredWorkflowId("query");
        String queryType = request.requiredQueryType();
        WorkflowStub stub = request.newWorkflowStub(workflowId);

        // Query result is returned as String. Workflow query handlers must return a String
        // or a type that Temporal's data converter serializes to a plain JSON string.
        String result = stub.query(queryType, String.class);

        LOG.info("Query '{}' on workflowId={} returned: {}", queryType, workflowId, result);
        // Use getMessage() consistently for all output
        exchange.getMessage().setBody(result);
        exchange.getMessage().setHeader(TemporalConstants.TEMPORAL_WORKFLOW_RESULT, result);
    }
}
