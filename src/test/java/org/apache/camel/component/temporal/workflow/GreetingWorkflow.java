package org.apache.camel.component.temporal.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Test workflow interface for unit tests.
 * This demonstrates a simple workflow that can be signaled and queried.
 */
@WorkflowInterface
public interface GreetingWorkflow {

    @WorkflowMethod
    String greet(String name);

    @SignalMethod
    void approve(String approver);

    @QueryMethod
    String getStatus();
}
