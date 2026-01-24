package com.lyshra.open.app.distributed.membership;

/**
 * Represents the status of a node in the cluster.
 *
 * The lifecycle of a node typically follows this progression:
 * <pre>
 * STARTING -> JOINING -> ACTIVE -> DRAINING -> SHUTTING_DOWN -> LEFT
 *                 |                    |
 *                 v                    v
 *              FAILED              FAILED
 * </pre>
 *
 * Additionally, a node can become UNREACHABLE at any point if heartbeats
 * are missed, and can transition to SUSPENDED for maintenance.
 */
public enum NodeStatus {

    /**
     * Node is starting up, initializing resources.
     */
    STARTING("Node is starting up", false, false),

    /**
     * Node is attempting to join the cluster.
     */
    JOINING("Node is joining the cluster", false, false),

    /**
     * Node is active and ready to accept work.
     */
    ACTIVE("Node is active", true, true),

    /**
     * Node is busy but healthy (high load).
     */
    BUSY("Node is busy", true, false),

    /**
     * Node is draining work in preparation for leaving.
     */
    DRAINING("Node is draining work", true, false),

    /**
     * Node is shutting down.
     */
    SHUTTING_DOWN("Node is shutting down", false, false),

    /**
     * Node has left the cluster gracefully.
     */
    LEFT("Node has left the cluster", false, false),

    /**
     * Node has failed (confirmed dead).
     */
    FAILED("Node has failed", false, false),

    /**
     * Node is unreachable (suspected failure).
     */
    UNREACHABLE("Node is unreachable", false, false),

    /**
     * Node is suspended (e.g., for maintenance).
     */
    SUSPENDED("Node is suspended", false, false),

    /**
     * Node status is unknown.
     */
    UNKNOWN("Node status is unknown", false, false);

    private final String description;
    private final boolean healthy;
    private final boolean acceptingWork;

    NodeStatus(String description, boolean healthy, boolean acceptingWork) {
        this.description = description;
        this.healthy = healthy;
        this.acceptingWork = acceptingWork;
    }

    /**
     * Gets a human-readable description of this status.
     *
     * @return the status description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this status indicates a healthy node.
     *
     * @return true if the node is healthy
     */
    public boolean isHealthy() {
        return healthy;
    }

    /**
     * Checks if the node can accept new work in this status.
     *
     * @return true if accepting work
     */
    public boolean isAcceptingWork() {
        return acceptingWork;
    }

    /**
     * Checks if this is a terminal status (node is leaving or has left).
     *
     * @return true if terminal
     */
    public boolean isTerminal() {
        return this == LEFT || this == FAILED;
    }

    /**
     * Checks if this is a transient status (will change automatically).
     *
     * @return true if transient
     */
    public boolean isTransient() {
        return this == STARTING || this == JOINING || this == SHUTTING_DOWN;
    }

    /**
     * Checks if this status indicates the node is leaving.
     *
     * @return true if leaving
     */
    public boolean isLeaving() {
        return this == DRAINING || this == SHUTTING_DOWN || this == LEFT;
    }

    /**
     * Checks if this status indicates a failure state.
     *
     * @return true if failed or unreachable
     */
    public boolean isFailed() {
        return this == FAILED || this == UNREACHABLE;
    }

    /**
     * Checks if transition to the target status is valid.
     *
     * @param target the target status
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(NodeStatus target) {
        if (target == null || target == this) {
            return false;
        }

        return switch (this) {
            case STARTING -> target == JOINING || target == FAILED || target == ACTIVE;
            case JOINING -> target == ACTIVE || target == FAILED || target == LEFT;
            case ACTIVE -> target == DRAINING || target == BUSY || target == SUSPENDED ||
                           target == UNREACHABLE || target == FAILED;
            case BUSY -> target == ACTIVE || target == DRAINING || target == UNREACHABLE || target == FAILED;
            case DRAINING -> target == SHUTTING_DOWN || target == FAILED || target == ACTIVE;
            case SHUTTING_DOWN -> target == LEFT || target == FAILED;
            case SUSPENDED -> target == ACTIVE || target == DRAINING || target == FAILED;
            case UNREACHABLE -> target == ACTIVE || target == FAILED;
            case LEFT, FAILED -> false; // Terminal states
            case UNKNOWN -> true; // Can transition to any state
        };
    }

    /**
     * Gets the status that should be set when a heartbeat is missed.
     *
     * @return the status for heartbeat miss
     */
    public NodeStatus onHeartbeatMissed() {
        if (isLeaving() || isTerminal()) {
            return this;
        }
        return UNREACHABLE;
    }

    /**
     * Gets the status that should be set when the node is confirmed dead.
     *
     * @return the failed status
     */
    public NodeStatus onConfirmedDead() {
        return FAILED;
    }

    /**
     * Gets the status that should be set when the node recovers.
     *
     * @return the recovered status
     */
    public NodeStatus onRecovered() {
        if (this == UNREACHABLE) {
            return ACTIVE;
        }
        return this;
    }
}
