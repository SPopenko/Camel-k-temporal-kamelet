# Camel-K Temporal Kamelet

An Apache Camel component and set of Camel-K Kamelets for integrating with [Temporal.io](https://temporal.io) workflow engine. Enables Camel routes and pipelines to start workflows, send signals to running workflows, and query workflow state — without writing any low-level Temporal SDK code.

---

## Features

| Kamelet | Operation | Description |
|---|---|---|
| `temporal-workflow-start-action` | `temporal:start` | Start a new Temporal workflow execution |
| `temporal-workflow-signal-action` | `temporal:signal` | Send a signal/event to a running workflow |
| `temporal-workflow-query-action` | `temporal:query` | Query the current state of a workflow |

---

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Docker & Docker Compose** — for running a local Temporal server (only needed for integration testing; unit tests use an in-memory environment)
- **`kubectl`, `kamel`, and `kind`** — only required for the Camel K end-to-end suite under `e2e/`

---

## Project Structure

```
├── pom.xml
├── docker-compose.yml
├── src/
│   ├── main/
│   │   ├── java/org/apache/camel/component/temporal/
│   │   │   ├── TemporalComponent.java         ← Camel component factory
│   │   │   ├── TemporalConfiguration.java     ← URI parameter config
│   │   │   ├── TemporalConstants.java         ← Exchange header names
│   │   │   ├── TemporalEndpoint.java          ← WorkflowClient lifecycle
│   │   │   ├── TemporalProducer.java          ← start/signal/query dispatch
│   │   │   └── TemporalRequestContext.java    ← Header/config resolution helpers
│   │   └── resources/
│   │       ├── META-INF/services/org/apache/camel/component/temporal
│   │       └── kamelets/
│   │           ├── temporal-workflow-start-action.kamelet.yaml
│   │           ├── temporal-workflow-signal-action.kamelet.yaml
│   │           └── temporal-workflow-query-action.kamelet.yaml
│   └── test/
│       ├── java/org/apache/camel/component/temporal/
│       │   ├── TemporalDockerIT.java
│       │   ├── TemporalProducerTest.java
│       │   ├── TemporalTestSupport.java
│       │   └── workflow/
│       │       ├── GreetingWorkflow.java
│       │       └── GreetingWorkflowImpl.java
│       └── resources/log4j2-test.xml
└── e2e/
    ├── apps/                                  ← Camel K route-runner and Temporal worker apps
    ├── k8s/                                   ← kind and Temporal manifests
    └── scripts/                               ← setup, scenario, and teardown scripts
```

---

## Build & Test

### Unit tests

Unit tests use Temporal's in-memory `TestWorkflowEnvironment` — no Docker or external server required.

```bash
mvn clean test
```

`mvn test` runs only the in-memory unit suite.

### Build the JAR

```bash
mvn clean package
```

### Local Temporal stack

Start the local Temporal stack from `docker-compose.yml`:

```bash
docker-compose up -d
```

Wait ~10 seconds for Temporal to initialize, then access:
- **Web UI**: http://localhost:8088
- **gRPC frontend**: `localhost:7233`
- **PostgreSQL**: `localhost:5432`

Stop the stack when finished:

```bash
docker-compose down
```

### Docker-backed integration tests

These tests require the local Temporal stack to be running. They execute the unit suite plus the live Docker-backed suite in `src/test/java/org/apache/camel/component/temporal/TemporalDockerIT.java`.

```bash
docker-compose up -d
mvn -Pdocker-it verify
docker-compose down
```

That suite starts a real Temporal worker in the test JVM and verifies:
- Java DSL `start`, `signal`, and `query`
- Kamelet `start`, `signal`, and `query`

### Camel K end-to-end suite on `kind`

This suite provisions a local `kind` cluster, installs Camel K, deploys Temporal inside Kubernetes, deploys a Temporal worker, then runs Camel K integrations that exercise the same `start`, `signal`, and `query` workflow flow end to end.

Prerequisites:
- Docker
- `kubectl`
- `kamel`
- network access to download `kind` on first run

Set up the environment:

```bash
./e2e/scripts/setup-kind.sh
```

Run the full scenario:

```bash
./e2e/scripts/run-camelk-e2e.sh
```

Tear the cluster down when finished:

```bash
./e2e/scripts/teardown-kind.sh
```

Notes:
- The Camel K suite uses `kind`, not `docker-compose`.
- The Camel K integrations run from self-managed images built from `e2e/apps`, while the operator still manages the deployment lifecycle.
- The Temporal worker image registers `GreetingWorkflowImpl` on the `greetings` task queue.
- The route runner image packages the Temporal component and Kamelets, then executes the `start`, `signal`, and `query` flows as one-shot Camel K integrations.

---

## Usage

### Component URI Format

```
temporal:start?host=localhost&port=7233&namespace=default&taskQueue=myQueue&workflowType=MyWorkflow
temporal:signal?host=localhost&port=7233&namespace=default&workflowId=myId&signalName=approve
temporal:query?host=localhost&port=7233&namespace=default&workflowId=myId&queryType=getStatus
```

If `host` already contains an explicit target such as `temporal.default.svc.cluster.local:7233`, the component uses it directly instead of appending `port`.

### Exchange Headers

Headers allow per-message parameter override at runtime:

| Header | Constant | Direction | Description |
|--------|----------|-----------|-------------|
| `CamelTemporalWorkflowId` | `TEMPORAL_WORKFLOW_ID` | in/out | Workflow instance ID |
| `CamelTemporalWorkflowRunId` | `TEMPORAL_WORKFLOW_RUN_ID` | out | Run ID (set after start) |
| `CamelTemporalSignalName` | `TEMPORAL_SIGNAL_NAME` | in | Override signal name |
| `CamelTemporalQueryType` | `TEMPORAL_QUERY_TYPE` | in | Override query type |
| `CamelTemporalWorkflowType` | `TEMPORAL_WORKFLOW_TYPE` | in | Override workflow type |
| `CamelTemporalTaskQueue` | `TEMPORAL_TASK_QUEUE` | in | Override task queue |
| `CamelTemporalWorkflowResult` | `TEMPORAL_WORKFLOW_RESULT` | out | Query result (set after query) |

For `query`, the current producer implementation expects the workflow query handler to return a `String`.

---

## Examples

### 1. Start a Workflow

**Java DSL:**
```java
from("timer:trigger?repeatCount=1")
    .setBody(constant("Alice"))
    .to("temporal:start?host=localhost&port=7233&namespace=default"
      + "&taskQueue=greetings&workflowType=GreetingWorkflow")
    .log("Started workflow: ${header.CamelTemporalWorkflowId}");
```

**Camel-K YAML route using the Kamelet:**
```yaml
- route:
    id: start-workflow
    from:
      uri: "timer:trigger"
      parameters:
        repeatCount: 1
    steps:
      - set-body:
          constant: "Alice"
      - kamelet:
          name: temporal-workflow-start-action
          properties:
            host: localhost
            port: 7233
            namespace: default
            taskQueue: greetings
            workflowType: GreetingWorkflow
      - log:
          message: "Started: ${header.CamelTemporalWorkflowId}"
```

### 2. Send a Signal to a Running Workflow

**Java DSL:**
```java
from("direct:approve")
    .setHeader("CamelTemporalWorkflowId", simple("${header.workflowId}"))
    .setBody(constant("manager"))
    .to("temporal:signal?host=localhost&port=7233&signalName=approve");
```

**Camel-K YAML route using the Kamelet:**
```yaml
- route:
    id: signal-workflow
    from:
      uri: "direct:approve"
    steps:
      - kamelet:
          name: temporal-workflow-signal-action
          properties:
            host: localhost
            port: 7233
            namespace: default
            workflowId: "my-workflow-id"
            signalName: approve
```

### 3. Query Workflow State

**Java DSL:**
```java
from("timer:poll?period=5000")
    .to("temporal:query?host=localhost&port=7233&workflowId=my-workflow&queryType=getStatus")
    .log("Current status: ${body}");
```

**Camel-K YAML route using the Kamelet:**
```yaml
- route:
    id: query-workflow
    from:
      uri: "timer:poll"
      parameters:
        period: 5000
    steps:
      - kamelet:
          name: temporal-workflow-query-action
          properties:
            host: localhost
            port: 7233
            namespace: default
            workflowId: "my-workflow-id"
            queryType: getStatus
      - log:
          message: "Status: ${body}"
```

---

## Configuration Reference

### Common Parameters (all operations)

| Parameter | Default | Required | Description |
|-----------|---------|----------|-------------|
| `host` | `localhost` | No | Temporal frontend hostname or explicit `host:port` target |
| `port` | `7233` | No | Temporal frontend gRPC port (used when `host` is not already an explicit target) |
| `namespace` | `default` | No | Temporal namespace |

### Start Operation Parameters

| Parameter | Default | Required | Description |
|-----------|---------|----------|-------------|
| `taskQueue` | — | **Yes** | Worker task queue name |
| `workflowType` | — | **Yes** | Workflow type name |
| `workflowId` | *(auto UUID)* | No | Workflow instance ID |
| `workflowExecutionTimeoutSeconds` | `3600` | No | Execution timeout (0 = unlimited) |

### Signal Operation Parameters

| Parameter | Default | Required | Description |
|-----------|---------|----------|-------------|
| `workflowId` | — | **Yes** | Running workflow instance ID |
| `signalName` | — | **Yes** | Signal method name on the workflow |

### Query Operation Parameters

| Parameter | Default | Required | Description |
|-----------|---------|----------|-------------|
| `workflowId` | — | **Yes** | Workflow instance ID |
| `queryType` | — | **Yes** | Query method name on the workflow |

---

## Using with Camel-K on Kubernetes

1. Install the Camel-K operator in your namespace
2. Build and deploy the component JAR to your local Maven repository or OCI registry
3. Reference the Kamelets in your Integration YAML with the `dependencies` section pointing to this artifact

```yaml
apiVersion: camel.apache.org/v1
kind: Integration
metadata:
  name: temporal-example
spec:
  dependencies:
    - mvn:org.apache.camel.kamelets:camel-temporal-kamelet:1.0.0-SNAPSHOT
  flows:
    - route:
        from:
          uri: "timer:tick?period=10000"
        steps:
          - kamelet:
              name: temporal-workflow-start-action
              properties:
                host: "temporal.default.svc.cluster.local"
                taskQueue: my-queue
                workflowType: MyWorkflow
```

---

## Implementing a Temporal Worker

The Camel component is a **client** — it starts, signals, and queries workflows. You also need a **worker** that runs the workflow code. Example using the Temporal Java SDK:

```java
// Define your workflow interface
@WorkflowInterface
public interface GreetingWorkflow {
    @WorkflowMethod
    String greet(String name);

    @SignalMethod
    void approve(String approver);

    @QueryMethod
    String getStatus();
}

// Implement it
public class GreetingWorkflowImpl implements GreetingWorkflow {
    private String status = "PENDING";
    private String approvedBy = null;

    @Override
    public String greet(String name) {
        status = "AWAITING_APPROVAL";
        Workflow.await(() -> approvedBy != null);
        status = "COMPLETED";
        return "Hello, " + name + "! Approved by: " + approvedBy;
    }

    @Override
    public void approve(String approver) {
        this.approvedBy = approver;
        this.status = "APPROVED";
    }

    @Override
    public String getStatus() { return status; }
}

// Start the worker
WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
WorkflowClient client = WorkflowClient.newInstance(service);
WorkerFactory factory = WorkerFactory.newInstance(client);
Worker worker = factory.newWorker("greetings");
worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
factory.start();
```

---

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE)
