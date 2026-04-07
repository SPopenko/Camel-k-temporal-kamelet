package org.apache.camel.component.temporal;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.worker.Worker;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kamelet.KameletComponent;
import org.apache.camel.component.temporal.workflow.GreetingWorkflow;
import org.apache.camel.component.temporal.workflow.GreetingWorkflowImpl;
import org.apache.camel.impl.DefaultCamelContext;

final class TemporalTestSupport {

    private TemporalTestSupport() {
    }

    static Worker registerGreetingWorker(io.temporal.testing.TestWorkflowEnvironment testEnv, String taskQueue) {
        Worker worker = testEnv.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
        return worker;
    }

    static DefaultCamelContext startCamelContext(boolean withKamelets, RouteBuilder... routes) throws Exception {
        DefaultCamelContext camelContext = new DefaultCamelContext();
        camelContext.addComponent("temporal", new TemporalComponent());

        if (withKamelets) {
            KameletComponent kameletComponent = camelContext.getComponent("kamelet", KameletComponent.class);
            kameletComponent.setLocation("classpath:kamelets");
        }

        for (RouteBuilder route : routes) {
            camelContext.addRoutes(route);
        }

        camelContext.start();
        return camelContext;
    }

    static void stopCamel(DefaultCamelContext camelContext, ProducerTemplate template) throws Exception {
        if (template != null) {
            template.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    static TemporalEndpoint useExternalWorkflowClient(DefaultCamelContext camelContext, String endpointUri,
                                                      WorkflowClient workflowClient) throws Exception {
        TemporalEndpoint endpoint = (TemporalEndpoint) camelContext.getEndpoint(endpointUri);
        endpoint.setExternalWorkflowClient(workflowClient);
        return endpoint;
    }

    static String startGreetingWorkflow(WorkflowClient workflowClient, String taskQueue, String name, String workflowIdPrefix) {
        String workflowId = workflowIdPrefix + UUID.randomUUID();
        GreetingWorkflow workflow = workflowClient.newWorkflowStub(
            GreetingWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(taskQueue)
                .build());
        WorkflowClient.start(workflow::greet, name);
        return workflowId;
    }

    static void signalWorkflow(WorkflowClient workflowClient, String workflowId, String signalName, String payload) {
        workflowClient.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.empty())
            .signal(signalName, payload);
    }

    static String waitForWorkflowStatus(WorkflowClient workflowClient, String workflowId, Duration timeout,
                                        String... expectedStates) {
        return waitForValue(
            "workflow status for " + workflowId,
            timeout,
            () -> queryWorkflowStatus(workflowClient, workflowId),
            value -> Arrays.asList(expectedStates).contains(value));
    }

    static String getWorkflowResult(WorkflowClient workflowClient, String workflowId, Duration timeout) {
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.empty());
        try {
            return stub.getResult(timeout.toSeconds(), TimeUnit.SECONDS, String.class);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Workflow did not complete in time: " + workflowId, e);
        }
    }

    static void waitForPort(String host, int port, Duration timeout, String errorMessage) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Exception lastError = null;

        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 1000);
                return;
            } catch (Exception e) {
                lastError = e;
                Thread.sleep(1000L);
            }
        }

        throw new IllegalStateException(errorMessage, lastError);
    }

    static <T> T waitForValue(String description, Duration timeout, Supplier<T> supplier,
                              java.util.function.Predicate<T> accepted) {
        long deadline = System.nanoTime() + timeout.toNanos();
        RuntimeException lastError = null;
        T lastValue = null;

        while (System.nanoTime() < deadline) {
            try {
                lastValue = supplier.get();
                if (accepted.test(lastValue)) {
                    return lastValue;
                }
            } catch (RuntimeException e) {
                lastError = e;
            }

            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for " + description, e);
            }
        }

        if (lastError != null) {
            throw new IllegalStateException("Timed out waiting for " + description, lastError);
        }
        throw new IllegalStateException("Timed out waiting for " + description + ", last value: " + lastValue);
    }

    private static String queryWorkflowStatus(WorkflowClient workflowClient, String workflowId) {
        return workflowClient.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.empty())
            .query("getStatus", String.class);
    }
}
