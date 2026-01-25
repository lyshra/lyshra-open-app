package com.lyshra.open.app.designer.domain;

/**
 * Types of connections between workflow steps.
 */
public enum ConnectionType {
    /**
     * Normal flow from one step to the next.
     */
    NORMAL,

    /**
     * Conditional branch based on processor output.
     */
    CONDITIONAL,

    /**
     * Error handling fallback connection.
     */
    ERROR_FALLBACK
}
