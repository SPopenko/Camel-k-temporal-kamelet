package org.apache.camel.component.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.time.Duration;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.temporal.workflow.GreetingWorkflowImpl;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalDockerIT {

    private static final String TEMPORAL_TARGET = "127.0.0.1:7233";
    private static final String NAMESPACE = "default";
    private static final String TASK_QUEUE = "greetings";

    private static WorkflowServiceStubs serviceStubs;
    private static WorkflowClient workflowClient;
    private static WorkerFactory workerFactory;

    private DefaultCamelContext camelContext;
    private ProducerTemplate template;

    @BeforeAll
    static void startWorker() throws Exception {
        TemporalTestSupport.waitForPort(
            "127.0.0.1",
            7233,
            Duration.ofSeconds(60),
            "Temporal is not reachable on localhost:7233. Start it with `docker-compose up -d` before running docker ITs.");

        serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder().setTarget(TEMPORAL_TARGET).build());
        workflowClient = WorkflowClient.newInstance(serviceStubs);
        workerFactory = WorkerFactory.newInstance(workflowClient);

        Worker worker = workerFactory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
        workerFactory.start();
    }

    @AfterAll
    static void stopWorker() {
        if (workerFactory != null) {
            workerFactory.shutdown();
        }
        if (serviceStubs != null) {
            serviceStubs.shutdown();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        camelContext = TemporalTestSupport.startCamelContext(true, dockerRoutes());
        template = camelContext.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        TemporalTestSupport.stopCamel(camelContext, template);
    }

    @Test
    void testJavaDslStartFlow() {
        Exchange exchange = template.request("direct:java-start", e -> e.getIn().setBody("Alice"));

        assertNull(exchange.getException());
        String workflowId = exchange.getMessage().getBody(String.class);
        assertNotNull(workflowId);
        assertNotNull(exchange.getMessage().getHeader(TemporalConstants.TEMPORAL_WORKFLOW_RUN_ID, String.class));
    }

    @Test
    void testJavaDslSignalFlow() {
        String workflowId = TemporalTestSupport.startGreetingWorkflow(workflowClient, TASK_QUEUE, "Bob", "docker-it-");

        Exchange exchange = template.request("direct:java-signal", e -> {
            e.getIn().setHeader("workflowId", workflowId);
            e.getIn().setBody("manager-java");
        });

        assertNull(exchange.getException());
        assertNull(exchange.getMessage().getBody());

        String result = TemporalTestSupport.getWorkflowResult(workflowClient, workflowId, Duration.ofSeconds(10));
        assertTrue(result.contains("manager-java"));
    }

    @Test
    void testJavaDslQueryFlow() {
        String workflowId = TemporalTestSupport.startGreetingWorkflow(workflowClient, TASK_QUEUE, "Charlie", "docker-it-");
        TemporalTestSupport.waitForWorkflowStatus(
            workflowClient,
            workflowId,
            Duration.ofSeconds(5),
            "PENDING",
            "AWAITING_APPROVAL");

        Exchange exchange = template.request("direct:java-query", e -> e.getIn().setHeader("workflowId", workflowId));

        assertNull(exchange.getException());
        String result = exchange.getMessage().getBody(String.class);
        assertTrue("PENDING".equals(result) || "AWAITING_APPROVAL".equals(result));

        TemporalTestSupport.signalWorkflow(workflowClient, workflowId, "approve", "cleanup-java");
    }

    @Test
    void testKameletStartFlow() {
        Exchange exchange = template.request("direct:kamelet-start", e -> e.getIn().setBody("Diana"));

        assertNull(exchange.getException());
        String workflowId = exchange.getMessage().getBody(String.class);
        assertNotNull(workflowId);
        assertNotNull(exchange.getMessage().getHeader(TemporalConstants.TEMPORAL_WORKFLOW_RUN_ID, String.class));
    }

    @Test
    void testKameletSignalFlow() {
        String workflowId = TemporalTestSupport.startGreetingWorkflow(workflowClient, TASK_QUEUE, "Eve", "docker-it-");

        Exchange exchange = template.request("direct:kamelet-signal", e -> {
            e.getIn().setHeader("workflowId", workflowId);
            e.getIn().setBody("manager-kamelet");
        });

        assertNull(exchange.getException());
        assertNull(exchange.getMessage().getBody());
        assertTrue(
            TemporalTestSupport.getWorkflowResult(workflowClient, workflowId, Duration.ofSeconds(10))
                .contains("manager-kamelet"));
    }

    @Test
    void testKameletQueryFlow() {
        String workflowId = TemporalTestSupport.startGreetingWorkflow(workflowClient, TASK_QUEUE, "Frank", "docker-it-");
        TemporalTestSupport.waitForWorkflowStatus(
            workflowClient,
            workflowId,
            Duration.ofSeconds(5),
            "PENDING",
            "AWAITING_APPROVAL");

        Exchange exchange = template.request("direct:kamelet-query", e -> e.getIn().setHeader("workflowId", workflowId));

        assertNull(exchange.getException());
        assertEquals(
            exchange.getMessage().getBody(String.class),
            exchange.getMessage().getHeader(TemporalConstants.TEMPORAL_WORKFLOW_RESULT, String.class));
        assertTrue(
            "PENDING".equals(exchange.getMessage().getBody(String.class))
                || "AWAITING_APPROVAL".equals(exchange.getMessage().getBody(String.class)));

        TemporalTestSupport.signalWorkflow(workflowClient, workflowId, "approve", "cleanup-kamelet");
    }

    private static String javaStartUri() {
        return "temporal:start?host=127.0.0.1&port=7233&namespace=" + NAMESPACE
            + "&taskQueue=" + TASK_QUEUE
            + "&workflowType=GreetingWorkflow"
            + "&workflowExecutionTimeoutSeconds=30";
    }

    private static String javaSignalUri() {
        return "temporal:signal?host=127.0.0.1&port=7233&namespace=" + NAMESPACE
            + "&signalName=approve";
    }

    private static String javaQueryUri() {
        return "temporal:query?host=127.0.0.1&port=7233&namespace=" + NAMESPACE
            + "&queryType=getStatus";
    }

    private static String kameletStartUri() {
        return "kamelet:temporal-workflow-start-action"
            + "?host=127.0.0.1"
            + "&port=7233"
            + "&namespace=" + NAMESPACE
            + "&taskQueue=" + TASK_QUEUE
            + "&workflowType=GreetingWorkflow"
            + "&workflowExecutionTimeoutSeconds=30";
    }

    private static String kameletSignalUri() {
        return "kamelet:temporal-workflow-signal-action"
            + "?host=127.0.0.1"
            + "&port=7233"
            + "&namespace=" + NAMESPACE
            + "&signalName=approve";
    }

    private static String kameletQueryUri() {
        return "kamelet:temporal-workflow-query-action"
            + "?host=127.0.0.1"
            + "&port=7233"
            + "&namespace=" + NAMESPACE
            + "&queryType=getStatus";
    }

    private RouteBuilder dockerRoutes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:java-start")
                    .to(javaStartUri());

                from("direct:java-signal")
                    .setHeader(TemporalConstants.TEMPORAL_WORKFLOW_ID, header("workflowId"))
                    .to(javaSignalUri());

                from("direct:java-query")
                    .setHeader(TemporalConstants.TEMPORAL_WORKFLOW_ID, header("workflowId"))
                    .to(javaQueryUri());

                from("direct:kamelet-start")
                    .to(kameletStartUri());

                from("direct:kamelet-signal")
                    .setHeader("workflowId", header("workflowId"))
                    .toD(kameletSignalUri() + "&workflowId=${header.workflowId}");

                from("direct:kamelet-query")
                    .toD(kameletQueryUri() + "&workflowId=${header.workflowId}");
            }
        };
    }
}
