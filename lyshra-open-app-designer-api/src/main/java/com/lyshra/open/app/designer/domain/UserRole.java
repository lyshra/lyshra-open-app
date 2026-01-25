package com.lyshra.open.app.designer.domain;

/**
 * User roles for authorization.
 */
public enum UserRole {
    /**
     * Administrator with full access.
     */
    ADMIN,

    /**
     * Developer who can create and edit workflows.
     */
    DEVELOPER,

    /**
     * Operator who can view and execute workflows.
     */
    OPERATOR,

    /**
     * Viewer with read-only access.
     */
    VIEWER
}
