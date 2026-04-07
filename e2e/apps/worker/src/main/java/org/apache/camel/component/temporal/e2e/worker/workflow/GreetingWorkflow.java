package org.apache.camel.component.temporal.e2e.worker.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface GreetingWorkflow {

    @WorkflowMethod
    String greet(String name);

    @SignalMethod
    void approve(String approver);

    @QueryMethod
    String getStatus();
}
