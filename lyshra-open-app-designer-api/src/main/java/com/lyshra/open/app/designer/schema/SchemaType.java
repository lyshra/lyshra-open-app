package com.lyshra.open.app.designer.schema;

/**
 * Enumeration of available JSON schema types.
 */
public enum SchemaType {

    WORKFLOW_DEFINITION("workflow-definition.schema.json"),
    WORKFLOW_VERSION("workflow-version.schema.json"),
    WORKFLOW_STEP("workflow-step.schema.json"),
    CONNECTION("connection.schema.json"),
    PROCESSOR_METADATA("processor-metadata.schema.json"),
    WORKFLOW_EXECUTION("workflow-execution.schema.json"),
    INDEX("index.schema.json");

    private final String fileName;

    SchemaType(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getResourcePath() {
        return "schema/" + fileName;
    }
}
