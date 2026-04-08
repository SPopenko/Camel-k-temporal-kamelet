# Camel Temporal Component — Agent Context

## Project Purpose
Custom Apache Camel component enabling Camel routes to interact with Temporal.io workflows (start workflow, send signals, query state) without low-level SDK code. Includes HTTP-based samples for local and Kubernetes deployment.

## Build
- Language: Java 17, Maven
- `mvn clean test` — run all unit tests (no server needed, uses in-memory Temporal)
- `mvn -Pdocker-it verify` — run unit tests plus Docker-backed integration tests against local Temporal
- `mvn clean package` — build the JAR
- `docker-compose up -d` — start local Temporal server (port 7233, Web UI on 8088)
- `mvn -f samples/pom.xml clean package` — build sample JARs
- `./samples/scripts/deploy.sh` — build images and deploy samples to kind cluster
- `./e2e/scripts/setup-kind.sh` / `./e2e/scripts/run-camelk-e2e.sh` — run the Camel K end-to-end suite on `kind`

## Project Structure
```
├── AGENTS.md                    ← this file
├── LICENSE
├── README.md
├── docker-compose.yml
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/org/apache/camel/component/temporal/
│   │   │   ├── TemporalComponent.java
│   │   │   ├── TemporalConfiguration.java
│   │   │   ├── TemporalConstants.java
│   │   │   ├── TemporalEndpoint.java
│   │   │   ├── TemporalProducer.java
│   │   │   └── TemporalRequestContext.java
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
│   ├── scripts/deploy.sh
│   ├── k8s/
│   │   ├── temporal.yaml
│   │   ├── worker-deployment.yaml
│   │   └── camel-http-temporal-deployment.yaml
│   ├── worker/                          ← Demo Temporal workflow worker
│   └── camel-http-temporal/             ← HTTP routes → Temporal component
└── e2e/
    ├── apps/
    │   ├── route-runner/
    │   └── worker/
    ├── k8s/
    └── scripts/
```

## Key Packages
- Main: `org.apache.camel.component.temporal`
- Tests: `org.apache.camel.component.temporal` + `org.apache.camel.component.temporal.workflow`
- Samples: `org.apache.camel.component.temporal.sample`

## Architecture Summary

### Camel Component URI Format
```
temporal:start?host=localhost&port=7233&namespace=default&taskQueue=myQueue&workflowType=MyWorkflow
temporal:signal?host=localhost&port=7233&namespace=default&workflowId=myId&signalName=approve
temporal:query?host=localhost&port=7233&namespace=default&workflowId=myId&queryType=getStatus
```

### Class Responsibilities
- `TemporalComponent` (extends DefaultComponent) — registers URI scheme `temporal`, creates endpoints
- `TemporalEndpoint` (extends DefaultEndpoint) — owns WorkflowClient lifecycle, creates producers, and supports injected clients for tests
- `TemporalProducer` (extends DefaultProducer) — dispatches start/signal/query operations using `exchange.getMessage()`
- `TemporalRequestContext` — resolves Exchange header overrides, validates required parameters, and serializes bodies
- `TemporalConfiguration` — `@UriParam`-annotated fields for all connection/operation config
- `TemporalConstants` — Exchange header name string constants

### Key Dependency Versions
- Camel: 4.8.0
- Temporal SDK: 1.34.0
- Java: 17
- JUnit Jupiter: 5.10.2

## Exchange Headers (TemporalConstants)
| Header | Constant | Description |
|--------|----------|-------------|
| `CamelTemporalWorkflowId` | `TEMPORAL_WORKFLOW_ID` | Override workflow ID per message |
| `CamelTemporalSignalName` | `TEMPORAL_SIGNAL_NAME` | Override signal name per message |
| `CamelTemporalQueryType` | `TEMPORAL_QUERY_TYPE` | Override query type per message |
| `CamelTemporalWorkflowType` | `TEMPORAL_WORKFLOW_TYPE` | Override workflow type per message |
| `CamelTemporalTaskQueue` | `TEMPORAL_TASK_QUEUE` | Override task queue per message |
| `CamelTemporalWorkflowRunId` | `TEMPORAL_WORKFLOW_RUN_ID` | Set by producer after workflow start |
| `CamelTemporalWorkflowResult` | `TEMPORAL_WORKFLOW_RESULT` | Set by producer after query |

## Test Approach
- In-memory Temporal via `TestWorkflowEnvironment` (JUnit 5 `@BeforeEach`/`@AfterEach`)
- **Important**: Do NOT use `TestWorkflowExtension` — it auto-generates a task queue that won't match test constants
- Instead: manually call `testEnv.newWorker(TASK_QUEUE)` to register on the correct queue
- Test workflow: `GreetingWorkflow` interface + `GreetingWorkflowImpl`
- No Docker required for unit tests
- Tests: testStartWorkflow, testStartWorkflowWithCustomId, testSignalWorkflow, testQueryWorkflow
- Inject in-memory client: `endpoint.setExternalWorkflowClient(workflowClient)`
- Docker-backed integration tests live in `TemporalDockerIT` and run via `mvn -Pdocker-it verify`
- Camel K end-to-end assets live under `e2e/` and cover the full `start -> query -> signal -> query` path on `kind`

## Samples
- `samples/worker/` — standalone Temporal worker running `GreetingWorkflowImpl` (configurable via `TEMPORAL_TARGET`, `TEMPORAL_NAMESPACE`, `TEMPORAL_TASK_QUEUE` env vars)
- `samples/camel-http-temporal/` — Camel app exposing HTTP endpoints that forward to the `temporal:` component:
  - `POST /workflow/start` → `temporal:start`
  - `POST /workflow/{workflowId}/signal/{signalName}` → `temporal:signal`
  - `GET /workflow/{workflowId}/query/{queryType}` → `temporal:query`
- `samples/scripts/deploy.sh` — builds images and deploys to kind cluster (assumes cluster already running)
- `samples/k8s/` — Kubernetes manifests for Temporal, worker, and Camel HTTP app

## Implementation Status
- [x] pom.xml
- [x] Java source files
- [x] Service discovery
- [x] Test files
- [x] docker-compose.yml
- [x] Docker-backed integration test profile
- [x] HTTP samples (worker + camel-http-temporal)
- [x] Kubernetes deployment manifests and deploy script
- [x] Camel K end-to-end harness under `e2e/`
- [x] README.md
- [x] Tests passing — `mvn clean test` → 4/4 GREEN
