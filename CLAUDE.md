# Camel-K Temporal Kamelet — Agent Context

## Project Purpose
Custom Apache Camel component + Kamelet YAML definitions enabling Camel-K routes to interact with Temporal.io workflows (start workflow, send signals, query state) without low-level SDK code.

## Build
- Language: Java 17, Maven
- `mvn clean test` — run all unit tests (no server needed, uses in-memory Temporal)
- `mvn clean package` — build the JAR
- `docker-compose up -d` — start local Temporal server (port 7233, Web UI on 8080)

## Project Structure
```
/home/user/Camel-k-temporal-kamelet/
├── CLAUDE.md                    ← this file
├── LICENSE
├── README.md
├── docker-compose.yml
├── pom.xml
└── src/
    ├── main/
    │   ├── java/org/apache/camel/component/temporal/
    │   │   ├── TemporalComponent.java
    │   │   ├── TemporalConfiguration.java
    │   │   ├── TemporalEndpoint.java
    │   │   ├── TemporalProducer.java
    │   │   └── TemporalConstants.java
    │   └── resources/
    │       ├── META-INF/services/org/apache/camel/component/temporal
    │       └── kamelets/
    │           ├── temporal-workflow-start-action.kamelet.yaml
    │           ├── temporal-workflow-signal-action.kamelet.yaml
    │           └── temporal-workflow-query-action.kamelet.yaml
    └── test/
        ├── java/org/apache/camel/component/temporal/
        │   ├── TemporalProducerTest.java
        │   └── workflow/
        │       ├── GreetingWorkflow.java
        │       └── GreetingWorkflowImpl.java
        └── resources/log4j2-test.xml
```

## Key Packages
- Main: `org.apache.camel.component.temporal`
- Tests: `org.apache.camel.component.temporal` + `org.apache.camel.component.temporal.workflow`

## Architecture Summary

### Camel Component URI Format
```
temporal://start?host=localhost&port=7233&namespace=default&taskQueue=myQueue&workflowType=MyWorkflow
temporal://signal?host=localhost&port=7233&namespace=default&workflowId=myId&signalName=approve
temporal://query?host=localhost&port=7233&namespace=default&workflowId=myId&queryType=getStatus
```

### Class Responsibilities
- `TemporalComponent` (extends DefaultComponent) — registers URI scheme `temporal://`, creates endpoints
- `TemporalEndpoint` (extends DefaultEndpoint) — owns WorkflowClient lifecycle, creates producers
- `TemporalProducer` (extends DefaultProducer) — dispatches start/signal/query operations
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
| `CamelTemporalWorkflowRunId` | `TEMPORAL_WORKFLOW_RUN_ID` | Set by producer after workflow start |
| `CamelTemporalWorkflowResult` | `TEMPORAL_WORKFLOW_RESULT` | Set by producer after query |

## Test Approach
- In-memory Temporal via `TestWorkflowEnvironment` / `TestWorkflowExtension` (JUnit 5)
- Test workflow: `GreetingWorkflow` interface + `GreetingWorkflowImpl`
- No Docker required for unit tests
- Tests: testStartWorkflow, testSignalWorkflow, testQueryWorkflow, testStartWorkflowWithCustomId

## Implementation Status
- [ ] pom.xml
- [ ] Java source files
- [ ] Kamelet YAMLs
- [ ] Service discovery
- [ ] Test files
- [ ] docker-compose.yml
- [ ] README.md
- [ ] Tests passing
