package org.apache.camel.component.temporal.sample.workflow;

import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInfo;
import org.slf4j.Logger;

public class GreetingWorkflowImpl implements GreetingWorkflow {

    private String status = "PENDING";
    private String approvedBy;

    @Override
    public String greet(String name) {
        Logger logger = Workflow.getLogger(GreetingWorkflowImpl.class);
        WorkflowInfo info = Workflow.getInfo();
        status = "AWAITING_APPROVAL";
        logger.info("WORKFLOW_STARTED workflowId={} name={}", info.getWorkflowId(), name);

        Workflow.await(() -> approvedBy != null);
        status = "COMPLETED";

        String result = "Hello, " + name + "! Approved by: " + approvedBy;
        logger.info("WORKFLOW_RESULT workflowId={} result={}", info.getWorkflowId(), result);
        return result;
    }

    @Override
    public void approve(String approver) {
        Logger logger = Workflow.getLogger(GreetingWorkflowImpl.class);
        approvedBy = approver;
        status = "APPROVED";
        logger.info("WORKFLOW_SIGNAL workflowId={} approvedBy={}", Workflow.getInfo().getWorkflowId(), approver);
    }

    @Override
    public String getStatus() {
        return status;
    }
}
