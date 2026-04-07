package org.apache.camel.component.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.temporal.workflow.GreetingWorkflow;
import org.apache.camel.component.temporal.workflow.GreetingWorkflowImpl;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemporalProducer using an in-memory Temporal test environment.
 * No external Temporal server is required.
 */
class TemporalProducerTest {

    static final String TASK_QUEUE = "test-greeting-queue";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient workflowClient;
    private DefaultCamelContext camelContext;
    private ProducerTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
        testEnv.start();
        workflowClient = testEnv.getWorkflowClient();

        camelContext = new DefaultCamelContext();
        camelContext.addComponent("temporal", new TemporalComponent());
        camelContext.start();
        template = camelContext.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (template != null) template.stop();
        if (camelContext != null) camelContext.stop();
        if (testEnv != null) testEnv.close();
    }

    /**
     * Test starting a workflow via the Camel temporal://start endpoint.
     * Verifies that the workflow is started and the workflowId is returned.
     */
    @Test
    void testStartWorkflow() throws Exception {
        String endpointUri = "temporal:start"
            + "?workflowType=GreetingWorkflow"
            + "&taskQueue=" + TASK_QUEUE
            + "&workflowExecutionTimeoutSeconds=10";

        TemporalEndpoint endpoint = (TemporalEndpoint) camelContext.getEndpoint(endpointUri);
        endpoint.setExternalWorkflowClient(workflowClient);

        Exchange exchange = template.request(endpointUri, e -> e.getIn().setBody("World"));

        assertNull(exchange.getException(), "No exception expected on start");

        String returnedWorkflowId = exchange.getMessage().getBody(String.class);
        assertNotNull(returnedWorkflowId, "WorkflowId should be returned in body");

        String headerWorkflowId = exchange.getMessage().getHeader(
            TemporalConstants.TEMPORAL_WORKFLOW_ID, String.class);
        assertEquals(returnedWorkflowId, headerWorkflowId,
            "WorkflowId in body and header should match");

        String runId = exchange.getMessage().getHeader(
            TemporalConstants.TEMPORAL_WORKFLOW_RUN_ID, String.class);
        assertNotNull(runId, "RunId header should be set after start");
    }

    /**
     * Test starting a workflow with a custom workflow ID supplied via header.
     */
    @Test
    void testStartWorkflowWithCustomId() throws Exception {
        String customId = "my-custom-workflow-id-" + System.currentTimeMillis();
        String endpointUri = "temporal:start"
            + "?workflowType=GreetingWorkflow"
            + "&taskQueue=" + TASK_QUEUE
            + "&workflowExecutionTimeoutSeconds=10";

        TemporalEndpoint endpoint = (TemporalEndpoint) camelContext.getEndpoint(endpointUri);
        endpoint.setExternalWorkflowClient(workflowClient);

        Exchange exchange = template.request(endpointUri, e -> {
            e.getIn().setBody("Alice");
            e.getIn().setHeader(TemporalConstants.TEMPORAL_WORKFLOW_ID, customId);
        });

        assertNull(exchange.getException(), "No exception expected");

        String returnedId = exchange.getMessage().getBody(String.class);
        assertEquals(customId, returnedId, "Custom workflowId should be used");
    }

    /**
     * Test sending a signal to a running workflow.
     */
    @Test
    void testSignalWorkflow() throws Exception {
        // Start a workflow directly via the SDK
        String workflowId = "signal-test-" + System.currentTimeMillis();
        WorkflowOptions startOptions = WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue(TASK_QUEUE)
            .build();
        GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class, startOptions);
        WorkflowClient.start(workflow::greet, "Bob");

        // Send signal via Camel
        String signalEndpointUri = "temporal:signal"
            + "?workflowId=" + workflowId
            + "&signalName=approve";

        TemporalEndpoint signalEndpoint = (TemporalEndpoint) camelContext.getEndpoint(signalEndpointUri);
        signalEndpoint.setExternalWorkflowClient(workflowClient);

        Exchange signalExchange = template.request(signalEndpointUri,
            e -> e.getIn().setBody("manager-user"));

        assertNull(signalExchange.getException(), "Signal should succeed");
        assertNull(signalExchange.getMessage().getBody(), "Signal response body should be null");

        // Verify the workflow completed after signal
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId,
            java.util.Optional.empty(), java.util.Optional.empty());
        String result = stub.getResult(5, java.util.concurrent.TimeUnit.SECONDS, String.class);
        assertNotNull(result, "Workflow should complete after signal");
        assertTrue(result.contains("manager-user"), "Result should mention the approver");
    }

    /**
     * Test querying workflow state.
     */
    @Test
    void testQueryWorkflow() throws Exception {
        // Start a workflow directly via the SDK
        String workflowId = "query-test-" + System.currentTimeMillis();
        WorkflowOptions startOptions = WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue(TASK_QUEUE)
            .build();
        GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class, startOptions);
        WorkflowClient.start(workflow::greet, "Charlie");

        // Give the workflow a moment to start and transition to AWAITING_APPROVAL
        Thread.sleep(200);

        // Query state via Camel
        String queryEndpointUri = "temporal:query"
            + "?workflowId=" + workflowId
            + "&queryType=getStatus";

        TemporalEndpoint queryEndpoint = (TemporalEndpoint) camelContext.getEndpoint(queryEndpointUri);
        queryEndpoint.setExternalWorkflowClient(workflowClient);

        Exchange queryExchange = template.request(queryEndpointUri, e -> e.getIn().setBody(null));

        assertNull(queryExchange.getException(), "Query should succeed");
        String state = queryExchange.getMessage().getBody(String.class);
        assertNotNull(state, "Query result should not be null");
        // State should be AWAITING_APPROVAL or PENDING (workflow may not have reached await yet)
        assertTrue(
            "AWAITING_APPROVAL".equals(state) || "PENDING".equals(state),
            "Unexpected workflow state: " + state
        );

        // Clean up: send approve signal so workflow can finish
        workflowClient.newUntypedWorkflowStub(workflowId,
            java.util.Optional.empty(), java.util.Optional.empty())
            .signal("approve", "cleanup-approver");
    }
}
