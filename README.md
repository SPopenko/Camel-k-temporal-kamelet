# Camel-K Temporal Kamelet

An Apache Camel component and set of Camel-K Kamelets for integrating with [Temporal.io](https://temporal.io) workflow engine. Enables Camel routes and pipelines to start workflows, send signals to running workflows, and query workflow state — without writing any low-level Temporal SDK code.

---

## Features

| Kamelet | Operation | Description |
|---|---|---|
| `temporal-workflow-start-action` | `temporal://start` | Start a new Temporal workflow execution |
| `temporal-workflow-signal-action` | `temporal://signal` | Send a signal/event to a running workflow |
| `temporal-workflow-query-action` | `temporal://query` | Query the current state of a workflow |

---

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Docker & Docker Compose** — for running a local Temporal server (only needed for integration testing; unit tests use an in-memory environment)

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
│   │   │   ├── TemporalEndpoint.java          ← WorkflowClient lifecycle
│   │   │   ├── TemporalProducer.java          ← start/signal/query dispatch
│   │   │   └── TemporalConstants.java         ← Exchange header names
│   │   └── resources/
│   │       ├── META-INF/services/org/apache/camel/component/temporal
│   │       └── kamelets/
│   │           ├── temporal-workflow-start-action.kamelet.yaml
│   │           ├── temporal-workflow-signal-action.kamelet.yaml
│   │           └── temporal-workflow-query-action.kamelet.yaml
│   └── test/
│       └── java/org/apache/camel/component/temporal/
│           ├── TemporalProducerTest.java
│           └── workflow/
│               ├── GreetingWorkflow.java
│               └── GreetingWorkflowImpl.java
```

---

## Build & Test

### Run unit tests (no server needed)

Unit tests use Temporal's in-memory `TestWorkflowEnvironment` — no Docker or external server required.

```bash
mvn clean test
```

### Build the JAR

```bash
mvn clean package
```

---

## Running Locally with Docker Compose

Start a local Temporal server (gRPC on port 7233, Web UI on port 8088):

```bash
docker-compose up -d
```

Wait ~10 seconds for Temporal to initialize, then access:
- **Web UI**: http://localhost:8088
- **gRPC frontend**: `localhost:7233`

Stop the server:

```bash
docker-compose down
```

---

## Usage

### Component URI Format

```
temporal://start?host=localhost&port=7233&namespace=default&taskQueue=myQueue&workflowType=MyWorkflow
temporal://signal?host=localhost&port=7233&namespace=default&workflowId=myId&signalName=approve
temporal://query?host=localhost&port=7233&namespace=default&workflowId=myId&queryType=getStatus
```

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

---

## Examples

### 1. Start a Workflow

**Java DSL:**
```java
from("timer:trigger?repeatCount=1")
    .setBody(constant("{\"name\":\"Alice\"}"))
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
          constant: '{"name":"Alice"}'
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
| `host` | `localhost` | No | Temporal frontend hostname |
| `port` | `7233` | No | Temporal frontend gRPC port |
| `namespace` | `default` | No | Temporal namespace |

### Start Operation Parameters

| Parameter | Default | Required | Description |
|-----------|---------|----------|-------------|
| `taskQueue` | — | **Yes** | Worker task queue name |
| `workflowType` | — | **Yes** | Workflow type (interface simple name) |
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
