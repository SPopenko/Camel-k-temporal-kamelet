package org.apache.camel.component.temporal;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

import java.io.Serializable;

/**
 * Configuration class for the Temporal Camel component.
 * All fields correspond to URI query parameters.
 */
@UriParams
public class TemporalConfiguration implements Cloneable, Serializable {

    @UriParam(defaultValue = "localhost", description = "Temporal frontend service hostname")
    private String host = "localhost";

    @UriParam(defaultValue = "7233", description = "Temporal frontend service gRPC port")
    private int port = 7233;

    @UriParam(defaultValue = "default", description = "Temporal namespace")
    private String namespace = "default";

    @UriParam(description = "Temporal task queue name (required for start operation)")
    private String taskQueue;

    @UriParam(description = "Workflow type name (required for start operation)")
    private String workflowType;

    @UriParam(description = "Workflow instance ID. Auto-generated UUID if blank for start operation.")
    private String workflowId;

    @UriParam(defaultValue = "3600", description = "Workflow execution timeout in seconds (0 = unlimited)")
    private long workflowExecutionTimeoutSeconds = 3600;

    @UriParam(description = "Signal method name (required for signal operation)")
    private String signalName;

    @UriParam(description = "Query method name (required for query operation)")
    private String queryType;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getTaskQueue() { return taskQueue; }
    public void setTaskQueue(String taskQueue) { this.taskQueue = taskQueue; }

    public String getWorkflowType() { return workflowType; }
    public void setWorkflowType(String workflowType) { this.workflowType = workflowType; }

    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

    public long getWorkflowExecutionTimeoutSeconds() { return workflowExecutionTimeoutSeconds; }
    public void setWorkflowExecutionTimeoutSeconds(long workflowExecutionTimeoutSeconds) {
        this.workflowExecutionTimeoutSeconds = workflowExecutionTimeoutSeconds;
    }

    public String getSignalName() { return signalName; }
    public void setSignalName(String signalName) { this.signalName = signalName; }

    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }

    @Override
    public TemporalConfiguration clone() {
        try {
            return (TemporalConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Clone not supported", e);
        }
    }
}
