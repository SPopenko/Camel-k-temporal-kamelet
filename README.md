# Camel Temporal Component

An Apache Camel component for integrating with [Temporal.io](https://temporal.io) workflow engine. Enables Camel routes to start workflows, send signals to running workflows, and query workflow state — without writing any low-level Temporal SDK code.

---

## Features

| Operation | URI | Description |
|---|---|---|
| Start | `temporal:start` | Start a new Temporal workflow execution |
| Signal | `temporal:signal` | Send a signal/event to a running workflow |
| Query | `temporal:query` | Query the current state of a workflow |

---

## Prerequisites

- **Java 17+**
- **Maven 3.8+**
- **Docker & Docker Compose** — for running a local Temporal server (only needed for integration testing; unit tests use an in-memory environment)
- **`kubectl` and `kind`** — only required for the Kubernetes samples under `samples/` and the e2e suite under `e2e/`

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
│   │       └── META-INF/services/org/apache/camel/component/temporal
│   └── test/
│       ├── java/org/apache/camel/component/temporal/
│       │   ├── TemporalDockerIT.java
│       │   ├── TemporalProducerTest.java
│       │   ├── TemporalTestSupport.java
│       │   └── workflow/
│       │       ├── GreetingWorkflow.java
│       │       └── GreetingWorkflowImpl.java
│       └── resources/log4j2-test.xml
├── samples/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── scripts/deploy.sh                     ← Build & deploy to kind cluster
│   ├── k8s/                                  ← Kubernetes manifests
│   ├── worker/                               ← Demo Temporal workflow worker
│   └── camel-http-temporal/                  ← Camel HTTP → Temporal routes
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

That suite starts a real Temporal worker in the test JVM and verifies Java DSL `start`, `signal`, and `query` operations.

---

## Samples

The `samples/` directory contains a complete working example of the Camel Temporal component exposed via HTTP endpoints.

### Running locally

1. Start Temporal:
   ```bash
   docker-compose up -d
   ```

2. Build the component and samples:
   ```bash
   mvn clean install -DskipTests
   mvn -f samples/pom.xml clean package
   ```

3. Start the demo workflow worker:
   ```bash
   java -jar samples/worker/target/app.jar
   ```

4. In another terminal, start the Camel HTTP app:
   ```bash
   java -jar samples/camel-http-temporal/target/app.jar
   ```

5. Test with curl:
   ```bash
   # Start a workflow
   curl -s -X POST http://localhost:8080/workflow/start \
     -H "Content-Type: application/json" -d '"Alice"'
   # Response: {"workflowId":"...","runId":"..."}

   # Query workflow status
   curl -s http://localhost:8080/workflow/<workflowId>/query/getStatus
   # Response: {"workflowId":"...","queryType":"getStatus","result":"AWAITING_APPROVAL"}

   # Signal the workflow (approve)
   curl -s -X POST http://localhost:8080/workflow/<workflowId>/signal/approve \
     -H "Content-Type: application/json" -d '"manager"'
   # Response: {"status":"signaled","workflowId":"..."}

   # Query again
   curl -s http://localhost:8080/workflow/<workflowId>/query/getStatus
   # Response: {"workflowId":"...","queryType":"getStatus","result":"COMPLETED"}
   ```

### Deploying to Kubernetes

Assumes a kind cluster is already running.

```bash
./samples/scripts/deploy.sh
```

The script builds Docker images, loads them into kind, and deploys Temporal + the worker + the Camel HTTP app. Once deployed:

```bash
kubectl -n camel-temporal-sample port-forward svc/camel-http-temporal 8080:8080
```

Then use the same curl commands above.

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

```java
from("timer:trigger?repeatCount=1")
    .setBody(constant("Alice"))
    .to("temporal:start?host=localhost&port=7233&namespace=default"
      + "&taskQueue=greetings&workflowType=GreetingWorkflow")
    .log("Started workflow: ${header.CamelTemporalWorkflowId}");
```

### 2. Send a Signal to a Running Workflow

```java
from("direct:approve")
    .setHeader("CamelTemporalWorkflowId", simple("${header.workflowId}"))
    .setBody(constant("manager"))
    .to("temporal:signal?host=localhost&port=7233&signalName=approve");
```

### 3. Query Workflow State

```java
from("timer:poll?period=5000")
    .to("temporal:query?host=localhost&port=7233&workflowId=my-workflow&queryType=getStatus")
    .log("Current status: ${body}");
```

### 4. YAML Route with HTTP (see `samples/camel-http-temporal`)

```yaml
- route:
    id: start-workflow
    from:
      uri: platform-http:/workflow/start
      parameters:
        httpMethodRestrict: POST
    steps:
      - to:
          uri: "temporal:start"
          parameters:
            host: localhost
            port: 7233
            taskQueue: greetings
            workflowType: GreetingWorkflow
      - setBody:
          simple: '{"workflowId":"${header.CamelTemporalWorkflowId}"}'
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
3. Reference the component in your Integration YAML:

```yaml
apiVersion: camel.apache.org/v1
kind: Integration
metadata:
  name: temporal-example
spec:
  dependencies:
    - mvn:org.apache.camel.component:camel-temporal:1.0.0-SNAPSHOT
  flows:
    - route:
        from:
          uri: "timer:tick?period=10000"
        steps:
          - to:
              uri: "temporal:start"
              parameters:
                host: "temporal.default.svc.cluster.local"
                taskQueue: my-queue
                workflowType: MyWorkflow
```

---

## Implementing a Temporal Worker

The Camel component is a **client** — it starts, signals, and queries workflows. You also need a **worker** that runs the workflow code. See `samples/worker/` for a complete example, or implement your own:

```java
@WorkflowInterface
public interface GreetingWorkflow {
    @WorkflowMethod
    String greet(String name);

    @SignalMethod
    void approve(String approver);

    @QueryMethod
    String getStatus();
}

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
