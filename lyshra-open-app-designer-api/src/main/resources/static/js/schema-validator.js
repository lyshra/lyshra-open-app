/**
 * Schema Validator - Frontend utility for JSON schema validation
 * Uses the backend schema validation API for consistent validation between frontend and backend.
 * Also caches schemas locally for faster client-side validation.
 */
class SchemaValidator {
    constructor() {
        this.schemas = new Map();
        this.schemaPromises = new Map();
        this.initialized = false;
    }

    /**
     * Initialize the validator by loading all schemas.
     */
    async initialize() {
        if (this.initialized) {
            return;
        }

        try {
            const response = await fetch('/api/v1/schemas/bundle', {
                headers: {
                    'Authorization': `Bearer ${getToken()}`,
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error('Failed to load schemas');
            }

            const schemas = await response.json();
            Object.entries(schemas).forEach(([type, schema]) => {
                this.schemas.set(type, schema);
            });

            this.initialized = true;
            console.log(`Loaded ${this.schemas.size} schemas`);
        } catch (error) {
            console.error('Failed to initialize schema validator:', error);
        }
    }

    /**
     * Get a schema by type.
     * @param {string} schemaType - Schema type (e.g., 'WORKFLOW_DEFINITION')
     * @returns {Promise<Object>} - The schema object
     */
    async getSchema(schemaType) {
        if (this.schemas.has(schemaType)) {
            return this.schemas.get(schemaType);
        }

        if (this.schemaPromises.has(schemaType)) {
            return this.schemaPromises.get(schemaType);
        }

        const promise = fetch(`/api/v1/schemas/${schemaType}`, {
            headers: {
                'Authorization': `Bearer ${getToken()}`,
                'Content-Type': 'application/json'
            }
        }).then(response => {
            if (!response.ok) {
                throw new Error(`Schema not found: ${schemaType}`);
            }
            return response.json();
        }).then(schema => {
            this.schemas.set(schemaType, schema);
            this.schemaPromises.delete(schemaType);
            return schema;
        });

        this.schemaPromises.set(schemaType, promise);
        return promise;
    }

    /**
     * Validate data against a schema using the backend API.
     * @param {Object} data - Data to validate
     * @param {string} schemaType - Schema type
     * @returns {Promise<ValidationResult>} - Validation result
     */
    async validate(data, schemaType) {
        try {
            const response = await fetch(`/api/v1/schemas/${schemaType}/validate`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${getToken()}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(data)
            });

            return await response.json();
        } catch (error) {
            console.error('Validation error:', error);
            return {
                valid: false,
                errors: [{
                    path: '$',
                    message: 'Validation request failed: ' + error.message,
                    keyword: 'network'
                }]
            };
        }
    }

    /**
     * Validate a workflow definition.
     * @param {Object} workflow - Workflow definition
     * @returns {Promise<ValidationResult>}
     */
    async validateWorkflowDefinition(workflow) {
        return this.validate(workflow, 'WORKFLOW_DEFINITION');
    }

    /**
     * Validate a workflow version.
     * @param {Object} version - Workflow version
     * @returns {Promise<ValidationResult>}
     */
    async validateWorkflowVersion(version) {
        return this.validate(version, 'WORKFLOW_VERSION');
    }

    /**
     * Validate a workflow step.
     * @param {Object} step - Workflow step
     * @returns {Promise<ValidationResult>}
     */
    async validateWorkflowStep(step) {
        return this.validate(step, 'WORKFLOW_STEP');
    }

    /**
     * Validate a connection.
     * @param {Object} connection - Connection
     * @returns {Promise<ValidationResult>}
     */
    async validateConnection(connection) {
        return this.validate(connection, 'CONNECTION');
    }

    /**
     * Get the list of available schema types.
     * @returns {Promise<Array<string>>}
     */
    async getAvailableSchemas() {
        const response = await fetch('/api/v1/schemas', {
            headers: {
                'Authorization': `Bearer ${getToken()}`,
                'Content-Type': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error('Failed to fetch schema list');
        }

        return response.json();
    }

    /**
     * Clear the schema cache.
     */
    clearCache() {
        this.schemas.clear();
        this.schemaPromises.clear();
        this.initialized = false;
    }
}

/**
 * Schema Constants
 */
const SchemaTypes = {
    WORKFLOW_DEFINITION: 'WORKFLOW_DEFINITION',
    WORKFLOW_VERSION: 'WORKFLOW_VERSION',
    WORKFLOW_STEP: 'WORKFLOW_STEP',
    CONNECTION: 'CONNECTION',
    PROCESSOR_METADATA: 'PROCESSOR_METADATA',
    WORKFLOW_EXECUTION: 'WORKFLOW_EXECUTION',
    INDEX: 'INDEX'
};

/**
 * Enum values from the schema for client-side use.
 */
const WorkflowEnums = {
    // Workflow Lifecycle States
    WorkflowLifecycleState: {
        DRAFT: 'DRAFT',
        ACTIVE: 'ACTIVE',
        DEPRECATED: 'DEPRECATED',
        ARCHIVED: 'ARCHIVED'
    },

    // Workflow Version States
    WorkflowVersionState: {
        DRAFT: 'DRAFT',
        ACTIVE: 'ACTIVE',
        DEPRECATED: 'DEPRECATED',
        ARCHIVED: 'ARCHIVED'
    },

    // Execution Status
    ExecutionStatus: {
        PENDING: 'PENDING',
        SCHEDULED: 'SCHEDULED',
        RUNNING: 'RUNNING',
        PAUSED: 'PAUSED',
        WAITING: 'WAITING',
        COMPLETED: 'COMPLETED',
        FAILED: 'FAILED',
        CANCELLED: 'CANCELLED',
        ABORTED: 'ABORTED',
        TIMED_OUT: 'TIMED_OUT'
    },

    // Step Types
    StepType: {
        START: 'START',
        END: 'END',
        PROCESSOR: 'PROCESSOR',
        SUB_WORKFLOW: 'SUB_WORKFLOW',
        DECISION: 'DECISION',
        PARALLEL_SPLIT: 'PARALLEL_SPLIT',
        PARALLEL_JOIN: 'PARALLEL_JOIN',
        LOOP: 'LOOP',
        WAIT: 'WAIT',
        ERROR_HANDLER: 'ERROR_HANDLER'
    },

    // Connection Types
    ConnectionType: {
        SEQUENTIAL: 'SEQUENTIAL',
        CONDITIONAL: 'CONDITIONAL',
        ERROR: 'ERROR',
        TIMEOUT: 'TIMEOUT',
        PARALLEL: 'PARALLEL',
        LOOP_BACK: 'LOOP_BACK',
        LOOP_EXIT: 'LOOP_EXIT'
    },

    // Processor Categories
    ProcessorCategory: {
        DATA_TRANSFORMATION: 'DATA_TRANSFORMATION',
        DATA_VALIDATION: 'DATA_VALIDATION',
        API_INTEGRATION: 'API_INTEGRATION',
        DATABASE: 'DATABASE',
        FILE_PROCESSING: 'FILE_PROCESSING',
        MESSAGING: 'MESSAGING',
        NOTIFICATION: 'NOTIFICATION',
        SECURITY: 'SECURITY',
        FLOW_CONTROL: 'FLOW_CONTROL',
        UTILITY: 'UTILITY',
        CUSTOM: 'CUSTOM'
    },

    // Field Types for Processor Configuration
    FieldType: {
        STRING: 'STRING',
        TEXT: 'TEXT',
        NUMBER: 'NUMBER',
        BOOLEAN: 'BOOLEAN',
        SELECT: 'SELECT',
        MULTI_SELECT: 'MULTI_SELECT',
        DATE: 'DATE',
        DATETIME: 'DATETIME',
        TIME: 'TIME',
        DURATION: 'DURATION',
        JSON: 'JSON',
        CODE: 'CODE',
        EXPRESSION: 'EXPRESSION',
        PASSWORD: 'PASSWORD',
        URL: 'URL',
        EMAIL: 'EMAIL',
        FILE: 'FILE',
        COLOR: 'COLOR',
        VARIABLE_REFERENCE: 'VARIABLE_REFERENCE',
        STEP_REFERENCE: 'STEP_REFERENCE',
        KEY_VALUE_PAIRS: 'KEY_VALUE_PAIRS',
        ARRAY: 'ARRAY'
    },

    // Error Handling Strategies
    ErrorStrategy: {
        FAIL: 'FAIL',
        SKIP: 'SKIP',
        RETRY: 'RETRY',
        FALLBACK: 'FALLBACK',
        GOTO: 'GOTO'
    },

    // Connection Style
    StrokeStyle: {
        SOLID: 'SOLID',
        DASHED: 'DASHED',
        DOTTED: 'DOTTED'
    },

    // Curve Types
    CurveType: {
        STRAIGHT: 'STRAIGHT',
        BEZIER: 'BEZIER',
        ORTHOGONAL: 'ORTHOGONAL',
        SMOOTH: 'SMOOTH'
    },

    // Node Shapes
    NodeShape: {
        RECTANGLE: 'RECTANGLE',
        ROUNDED: 'ROUNDED',
        DIAMOND: 'DIAMOND',
        CIRCLE: 'CIRCLE',
        PARALLELOGRAM: 'PARALLELOGRAM'
    }
};

/**
 * Helper function to create validation error display.
 * @param {ValidationResult} result - Validation result
 * @returns {string} - HTML string for error display
 */
function renderValidationErrors(result) {
    if (result.valid) {
        return '<div class="alert alert-success">Validation passed</div>';
    }

    const errors = result.errors.map(error => `
        <li class="list-group-item list-group-item-danger">
            <strong>${escapeHtml(error.path)}</strong>: ${escapeHtml(error.message)}
            ${error.keyword ? `<span class="badge bg-secondary">${error.keyword}</span>` : ''}
        </li>
    `).join('');

    return `
        <div class="alert alert-danger">
            <h6><i class="bi bi-exclamation-triangle"></i> Validation Failed</h6>
            <p>${result.errorCount || result.errors.length} error(s) found</p>
        </div>
        <ul class="list-group">
            ${errors}
        </ul>
    `;
}

// Create global instance
const schemaValidator = new SchemaValidator();
