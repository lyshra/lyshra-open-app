package com.lyshra.open.app.designer.exception;

/**
 * Exception thrown when a requested resource is not found.
 */
public class ResourceNotFoundException extends DesignerException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super("NOT_FOUND", String.format("%s not found with ID: %s", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
