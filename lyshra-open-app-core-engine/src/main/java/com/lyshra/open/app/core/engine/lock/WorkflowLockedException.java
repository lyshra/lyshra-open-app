package com.lyshra.open.app.core.engine.lock;

/**
 * Exception thrown when an operation cannot proceed because the
 * target workflow instance is locked by another process.
 *
 * <p>This exception is typically thrown when:
 * <ul>
 *   <li>Attempting to resume a workflow that's already being resumed</li>
 *   <li>Attempting to modify a workflow state while it's locked</li>
 *   <li>Lock acquisition timeout is exceeded</li>
 * </ul>
 */
public class WorkflowLockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String workflowInstanceId;
    private final String currentOwner;
    private final WorkflowLock lockInfo;

    /**
     * Creates an exception with a message.
     */
    public WorkflowLockedException(String message) {
        super(message);
        this.workflowInstanceId = null;
        this.currentOwner = null;
        this.lockInfo = null;
    }

    /**
     * Creates an exception with workflow instance ID.
     */
    public WorkflowLockedException(String message, String workflowInstanceId) {
        super(message);
        this.workflowInstanceId = workflowInstanceId;
        this.currentOwner = null;
        this.lockInfo = null;
    }

    /**
     * Creates an exception with full lock information.
     */
    public WorkflowLockedException(String message, String workflowInstanceId,
                                   String currentOwner, WorkflowLock lockInfo) {
        super(message);
        this.workflowInstanceId = workflowInstanceId;
        this.currentOwner = currentOwner;
        this.lockInfo = lockInfo;
    }

    /**
     * Creates an exception with lock info.
     */
    public WorkflowLockedException(String message, WorkflowLock lockInfo) {
        super(message);
        this.workflowInstanceId = lockInfo != null ? lockInfo.getWorkflowInstanceId() : null;
        this.currentOwner = lockInfo != null ? lockInfo.getOwnerId() : null;
        this.lockInfo = lockInfo;
    }

    /**
     * Gets the ID of the locked workflow instance.
     */
    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    /**
     * Gets the ID of the current lock owner.
     */
    public String getCurrentOwner() {
        return currentOwner;
    }

    /**
     * Gets the full lock information if available.
     */
    public WorkflowLock getLockInfo() {
        return lockInfo;
    }

    /**
     * Creates an exception for a specific workflow and owner.
     */
    public static WorkflowLockedException lockedBy(String workflowInstanceId, String currentOwner) {
        return new WorkflowLockedException(
                String.format("Workflow %s is currently locked by %s", workflowInstanceId, currentOwner),
                workflowInstanceId,
                currentOwner,
                null);
    }

    /**
     * Creates an exception for a lock acquisition timeout.
     */
    public static WorkflowLockedException timeout(String workflowInstanceId) {
        return new WorkflowLockedException(
                String.format("Timeout waiting for lock on workflow %s", workflowInstanceId),
                workflowInstanceId);
    }

    /**
     * Creates an exception with lock info.
     */
    public static WorkflowLockedException withLockInfo(WorkflowLock lockInfo) {
        return new WorkflowLockedException(
                String.format("Workflow %s is locked by %s since %s (expires %s)",
                        lockInfo.getWorkflowInstanceId(),
                        lockInfo.getOwnerId(),
                        lockInfo.getAcquiredAt(),
                        lockInfo.getExpiresAt()),
                lockInfo);
    }
}
