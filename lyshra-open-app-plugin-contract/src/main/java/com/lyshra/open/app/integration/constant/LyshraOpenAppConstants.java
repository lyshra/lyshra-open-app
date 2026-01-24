package com.lyshra.open.app.integration.constant;

public interface LyshraOpenAppConstants {

    String DEFAULT_BRANCH = "DEFAULT";
    String NOOP_PROCESSOR = "NOOP";

    String DEFAULT_ERROR_CONFIG_KEY = "DEFAULT";
    String TIMEOUT_ERROR_CONFIG_KEY = "TIMEOUT";

    String DEFAULT_CONFIG = "DEFAULT";

    // Human Task related constants
    /**
     * Branch indicating workflow should suspend and wait for human task completion.
     */
    String WAITING_BRANCH = "WAITING";

    /**
     * Branch indicating human task was approved.
     */
    String APPROVED_BRANCH = "APPROVED";

    /**
     * Branch indicating human task was rejected.
     */
    String REJECTED_BRANCH = "REJECTED";

    /**
     * Branch indicating human task timed out.
     */
    String TIMED_OUT_BRANCH = "TIMED_OUT";

    /**
     * Branch indicating human task was cancelled.
     */
    String CANCELLED_BRANCH = "CANCELLED";

    /**
     * Branch indicating human task was completed with data.
     */
    String COMPLETED_BRANCH = "COMPLETED";
}
