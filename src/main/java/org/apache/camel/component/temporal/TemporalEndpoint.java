package org.apache.camel.component.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporal endpoint. Producer-only: supports start, signal, and query operations.
 */
@UriEndpoint(
    firstVersion = "1.0.0",
    scheme = "temporal",
    title = "Temporal",
    syntax = "temporal:operation",
    producerOnly = true
)
public class TemporalEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(TemporalEndpoint.class);

    @UriPath(description = "Operation: start, signal, or query")
    private final String operation;

    @UriParam
    private final TemporalConfiguration configuration;

    private volatile WorkflowServiceStubs serviceStubs;
    private volatile WorkflowClient workflowClient;

    public TemporalEndpoint(String uri, TemporalComponent component,
                            String operation, TemporalConfiguration configuration) {
        super(uri, component);
        this.operation = operation;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new TemporalProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException(
            "The Temporal component does not support consuming. " +
            "Use start/signal/query producer operations only.");
    }

    /**
     * Returns a lazily-initialized, cached WorkflowClient.
     * If an external client was injected (for testing), returns that directly.
     */
    public WorkflowClient getWorkflowClient() {
        if (workflowClient == null) {
            synchronized (this) {
                if (workflowClient == null) {
                    String target = configuration.getHost() + ":" + configuration.getPort();
                    LOG.info("Creating Temporal WorkflowClient connecting to {}", target);
                    WorkflowServiceStubsOptions stubOptions =
                        WorkflowServiceStubsOptions.newBuilder()
                            .setTarget(target)
                            .build();
                    serviceStubs = WorkflowServiceStubs.newServiceStubs(stubOptions);
                    WorkflowClientOptions clientOptions =
                        WorkflowClientOptions.newBuilder()
                            .setNamespace(configuration.getNamespace())
                            .build();
                    workflowClient = WorkflowClient.newInstance(serviceStubs, clientOptions);
                }
            }
        }
        return workflowClient;
    }

    /**
     * Injects an externally managed WorkflowClient (e.g., from TestWorkflowEnvironment).
     * Must be called before first use.
     */
    public synchronized void setExternalWorkflowClient(WorkflowClient client) {
        this.workflowClient = client;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (serviceStubs != null) {
            try {
                serviceStubs.shutdown();
            } catch (Exception e) {
                LOG.warn("Error shutting down Temporal service stubs", e);
            }
            serviceStubs = null;
        }
        workflowClient = null;
    }

    public String getOperation() { return operation; }
    public TemporalConfiguration getConfiguration() { return configuration; }
}
