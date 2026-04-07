package org.apache.camel.component.temporal.workflow;

import io.temporal.workflow.Workflow;

/**
 * Test workflow implementation.
 * Waits for an "approve" signal before completing.
 */
public class GreetingWorkflowImpl implements GreetingWorkflow {

    private String status = "PENDING";
    private String approvedBy = null;

    @Override
    public String greet(String name) {
        status = "AWAITING_APPROVAL";
        // Block until approved via signal
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
    public String getStatus() {
        return status;
    }
}
