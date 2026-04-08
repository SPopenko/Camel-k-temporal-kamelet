package org.apache.camel.component.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

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
        TemporalTestSupport.registerGreetingWorker(testEnv, TASK_QUEUE);
        testEnv.start();
        workflowClient = testEnv.getWorkflowClient();

        camelContext = TemporalTestSupport.startCamelContext();
        template = camelContext.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        TemporalTestSupport.stopCamel(camelContext, template);
        if (testEnv != null) {
            testEnv.close();
        }
    }

    /**
     * Test starting a workflow via the Camel temporal:start endpoint.
     * Verifies that the workflow is started and the workflowId is returned.
     */
    @Test
    void testStartWorkflow() throws Exception {
        String endpointUri = "temporal:start"
            + "?workflowType=GreetingWorkflow"
            + "&taskQueue=" + TASK_QUEUE
            + "&workflowExecutionTimeoutSeconds=10";

        TemporalTestSupport.useExternalWorkflowClient(camelContext, endpointUri, workflowClient);

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

        TemporalTestSupport.useExternalWorkflowClient(camelContext, endpointUri, workflowClient);

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
        String workflowId = TemporalTestSupport.startGreetingWorkflow(workflowClient, TASK_QUEUE, "Bob", "signal-test-");

        // Send signal via Camel
        String signalEndpointUri = "temporal:signal"
            + "?workflowId=" + workflowId
            + "&signalName=approve";

        TemporalTestSupport.useExternalWorkflowClient(camelContext, signalEndpointUri, workflowClient);

        Exchange signalExchange = template.request(signalEndpointUri,
            e -> e.getIn().setBody("manager-user"));

        assertNull(signalExchange.getException(), "Signal should succeed");
        assertNull(signalExchange.getMessage().getBody(), "Signal response body should be null");

        // Verify the workflow completed after signal
        String result = TemporalTestSupport.getWorkflowResult(workflowClient, workflowId, Duration.ofSeconds(5));
        assertNotNull(result, "Workflow should complete after signal");
        assertTrue(result.contains("manager-user"), "Result should mention the approver");
    }

    /**
     * Test querying workflow state.
     */
    @Test
    void testQueryWorkflow() throws Exception {
        String workflowId = TemporalTestSupport.startGreetingWorkflow(workflowClient, TASK_QUEUE, "Charlie", "query-test-");
        TemporalTestSupport.waitForWorkflowStatus(
            workflowClient,
            workflowId,
            Duration.ofSeconds(5),
            "AWAITING_APPROVAL",
            "PENDING");

        // Query state via Camel
        String queryEndpointUri = "temporal:query"
            + "?workflowId=" + workflowId
            + "&queryType=getStatus";

        TemporalTestSupport.useExternalWorkflowClient(camelContext, queryEndpointUri, workflowClient);

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
        TemporalTestSupport.signalWorkflow(workflowClient, workflowId, "approve", "cleanup-approver");
    }
}
