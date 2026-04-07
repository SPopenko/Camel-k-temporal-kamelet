package org.apache.camel.component.temporal;

/**
 * Constants for Exchange header names used by the Temporal Camel component.
 */
public final class TemporalConstants {

    private TemporalConstants() {
    }

    /** Header to override or receive the workflow instance ID. */
    public static final String TEMPORAL_WORKFLOW_ID = "CamelTemporalWorkflowId";

    /** Header to override the signal method name. */
    public static final String TEMPORAL_SIGNAL_NAME = "CamelTemporalSignalName";

    /** Header to override the query type/method name. */
    public static final String TEMPORAL_QUERY_TYPE = "CamelTemporalQueryType";

    /** Header set by producer after a successful workflow start, containing the run ID. */
    public static final String TEMPORAL_WORKFLOW_RUN_ID = "CamelTemporalWorkflowRunId";

    /** Header set by producer after a successful query, containing the query result. */
    public static final String TEMPORAL_WORKFLOW_RESULT = "CamelTemporalWorkflowResult";

    /** Header to override the workflow type name. */
    public static final String TEMPORAL_WORKFLOW_TYPE = "CamelTemporalWorkflowType";

    /** Header to override the task queue name. */
    public static final String TEMPORAL_TASK_QUEUE = "CamelTemporalTaskQueue";
}
