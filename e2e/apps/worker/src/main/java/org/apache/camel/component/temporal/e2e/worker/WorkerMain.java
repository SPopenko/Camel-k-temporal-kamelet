package org.apache.camel.component.temporal.e2e.worker;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.util.concurrent.CountDownLatch;
import org.apache.camel.component.temporal.e2e.worker.workflow.GreetingWorkflowImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorkerMain {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerMain.class);

    private WorkerMain() {
    }

    public static void main(String[] args) throws Exception {
        String temporalTarget = env("TEMPORAL_TARGET",
            "dns:///temporal-frontend.camel-k-temporal-e2e.svc.cluster.local:7233");
        String namespace = env("TEMPORAL_NAMESPACE", "default");
        String taskQueue = env("TEMPORAL_TASK_QUEUE", "greetings");

        WorkflowServiceStubs serviceStubs = WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(temporalTarget)
                .build());
        WorkflowClient workflowClient = WorkflowClient.newInstance(
            serviceStubs,
            WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build());

        WorkerFactory workerFactory = WorkerFactory.newInstance(workflowClient);
        Worker worker = workerFactory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Stopping Temporal worker");
            workerFactory.shutdown();
            serviceStubs.shutdown();
        }));

        workerFactory.start();
        LOG.info("TEMPORAL_WORKER_READY target={} namespace={} taskQueue={}", temporalTarget, namespace, taskQueue);

        new CountDownLatch(1).await();
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
