package org.apache.camel.component.temporal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.apache.camel.Exchange;

final class TemporalRequestContext {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Exchange exchange;
    private final TemporalConfiguration configuration;
    private final WorkflowClient workflowClient;

    TemporalRequestContext(TemporalEndpoint endpoint, Exchange exchange) {
        this.exchange = exchange;
        this.configuration = endpoint.getConfiguration();
        this.workflowClient = endpoint.getWorkflowClient();
    }

    WorkflowClient workflowClient() {
        return workflowClient;
    }

    String startWorkflowId() {
        String workflowId = resolve(TemporalConstants.TEMPORAL_WORKFLOW_ID, configuration.getWorkflowId());
        return isBlank(workflowId) ? UUID.randomUUID().toString() : workflowId;
    }

    String requiredWorkflowType() {
        return require(TemporalConstants.TEMPORAL_WORKFLOW_TYPE, configuration.getWorkflowType(), "workflowType", "start");
    }

    String requiredTaskQueue() {
        return require(TemporalConstants.TEMPORAL_TASK_QUEUE, configuration.getTaskQueue(), "taskQueue", "start");
    }

    String requiredWorkflowId(String operation) {
        return require(TemporalConstants.TEMPORAL_WORKFLOW_ID, configuration.getWorkflowId(), "workflowId", operation);
    }

    String requiredSignalName() {
        return require(TemporalConstants.TEMPORAL_SIGNAL_NAME, configuration.getSignalName(), "signalName", "signal");
    }

    String requiredQueryType() {
        return require(TemporalConstants.TEMPORAL_QUERY_TYPE, configuration.getQueryType(), "queryType", "query");
    }

    WorkflowStub newStartWorkflowStub(String workflowType, String workflowId, String taskQueue) {
        return workflowClient.newUntypedWorkflowStub(workflowType, newWorkflowOptions(workflowId, taskQueue));
    }

    WorkflowStub newWorkflowStub(String workflowId) {
        return workflowClient.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.empty());
    }

    String serializedBody() throws Exception {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return null;
        }
        if (body instanceof String s) {
            return s;
        }
        return OBJECT_MAPPER.writeValueAsString(body);
    }

    private WorkflowOptions newWorkflowOptions(String workflowId, String taskQueue) {
        WorkflowOptions.Builder optionsBuilder = WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue(taskQueue);

        if (configuration.getWorkflowExecutionTimeoutSeconds() > 0) {
            optionsBuilder.setWorkflowExecutionTimeout(
                Duration.ofSeconds(configuration.getWorkflowExecutionTimeoutSeconds()));
        }

        return optionsBuilder.build();
    }

    private String resolve(String headerName, String fallback) {
        String value = exchange.getIn().getHeader(headerName, String.class);
        return isBlank(value) ? fallback : value;
    }

    private String require(String headerName, String fallback, String parameterName, String operation) {
        String value = resolve(headerName, fallback);
        if (isBlank(value)) {
            throw new IllegalArgumentException(
                "Parameter '" + parameterName + "' is required for operation: " + operation);
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
