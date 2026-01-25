/**
 * Workflow Validator - Real-time validation for workflow consistency
 * Validates workflow structure, connections, and step configurations.
 */

class WorkflowValidator {
    constructor() {
        this.validationRules = new Map();
        this.processorMetadata = new Map();
        this.initializeRules();
    }

    /**
     * Initialize validation rules
     */
    initializeRules() {
        // Connection type rules
        this.connectionRules = {
            PROCESSOR: {
                allowedOutputTypes: ['SEQUENTIAL', 'CONDITIONAL', 'ERROR'],
                allowedInputTypes: ['SEQUENTIAL', 'CONDITIONAL', 'ERROR', 'LOOP_BACK'],
                maxOutputConnections: null, // unlimited
                maxInputConnections: null,
                requiresOutput: false
            },
            START: {
                allowedOutputTypes: ['SEQUENTIAL'],
                allowedInputTypes: [],
                maxOutputConnections: 1,
                maxInputConnections: 0,
                requiresOutput: true
            },
            END: {
                allowedOutputTypes: [],
                allowedInputTypes: ['SEQUENTIAL', 'CONDITIONAL', 'ERROR'],
                maxOutputConnections: 0,
                maxInputConnections: null,
                requiresOutput: false
            },
            DECISION: {
                allowedOutputTypes: ['CONDITIONAL'],
                allowedInputTypes: ['SEQUENTIAL', 'CONDITIONAL'],
                maxOutputConnections: null,
                maxInputConnections: null,
                requiresOutput: true,
                minOutputConnections: 2
            },
            PARALLEL_SPLIT: {
                allowedOutputTypes: ['PARALLEL'],
                allowedInputTypes: ['SEQUENTIAL'],
                maxOutputConnections: null,
                maxInputConnections: 1,
                requiresOutput: true,
                minOutputConnections: 2
            },
            PARALLEL_JOIN: {
                allowedOutputTypes: ['SEQUENTIAL'],
                allowedInputTypes: ['PARALLEL'],
                maxOutputConnections: 1,
                maxInputConnections: null,
                requiresOutput: true,
                minInputConnections: 2
            },
            LOOP: {
                allowedOutputTypes: ['SEQUENTIAL', 'LOOP_EXIT'],
                allowedInputTypes: ['SEQUENTIAL', 'LOOP_BACK'],
                maxOutputConnections: 2,
                maxInputConnections: null,
                requiresOutput: true
            },
            ERROR_HANDLER: {
                allowedOutputTypes: ['SEQUENTIAL'],
                allowedInputTypes: ['ERROR'],
                maxOutputConnections: 1,
                maxInputConnections: null,
                requiresOutput: false
            }
        };

        // Processor-specific validators
        this.processorValidators = {
            'IfConditionProcessor': this.validateIfCondition.bind(this),
            'SwitchProcessor': this.validateSwitch.bind(this),
            'ForEachProcessor': this.validateForEach.bind(this),
            'HttpRequestProcessor': this.validateHttpRequest.bind(this),
            'JavaScriptProcessor': this.validateJavaScript.bind(this),
            'SqlQueryProcessor': this.validateSqlQuery.bind(this),
            'MongoDbQueryProcessor': this.validateMongoQuery.bind(this),
            'EmailSendProcessor': this.validateEmailSend.bind(this),
            'SetVariableProcessor': this.validateSetVariable.bind(this)
        };
    }

    /**
     * Register processor metadata for validation
     */
    registerProcessor(processorName, metadata) {
        this.processorMetadata.set(processorName, metadata);
    }

    /**
     * Validate entire workflow
     * @param {Object} workflow - Workflow data with steps and connections
     * @returns {ValidationResult}
     */
    validateWorkflow(workflow) {
        const result = {
            valid: true,
            errors: [],
            warnings: [],
            stepErrors: new Map(),
            connectionErrors: new Map()
        };

        const steps = workflow.steps || [];
        const connections = workflow.connections || {};
        const startStepId = workflow.startStepId;

        // Convert to Map for easier access
        const stepsMap = new Map(steps.map(s => [s.id, s]));
        const connectionsMap = new Map(Object.entries(connections));

        // 1. Validate workflow structure
        this.validateWorkflowStructure(result, stepsMap, startStepId);

        // 2. Validate each step
        for (const [stepId, step] of stepsMap) {
            this.validateStep(result, step, stepsMap, connectionsMap);
        }

        // 3. Validate connections
        for (const [connId, conn] of connectionsMap) {
            this.validateConnection(result, conn, stepsMap);
        }

        // 4. Validate workflow flow (reachability, cycles, etc.)
        this.validateWorkflowFlow(result, stepsMap, connectionsMap, startStepId);

        // 5. Validate expression references
        this.validateExpressionReferences(result, steps, connectionsMap);

        result.valid = result.errors.length === 0;
        return result;
    }

    /**
     * Validate workflow structure
     */
    validateWorkflowStructure(result, stepsMap, startStepId) {
        // Must have at least one step
        if (stepsMap.size === 0) {
            result.errors.push({
                code: 'NO_STEPS',
                message: 'Workflow must have at least one step',
                severity: 'error'
            });
        }

        // Must have a start step
        if (!startStepId) {
            result.errors.push({
                code: 'NO_START_STEP',
                message: 'Workflow must have a start step defined',
                severity: 'error'
            });
        } else if (!stepsMap.has(startStepId)) {
            result.errors.push({
                code: 'INVALID_START_STEP',
                message: 'Start step reference is invalid - step does not exist',
                severity: 'error'
            });
        }

        // Check for duplicate step names
        const stepNames = new Map();
        for (const [stepId, step] of stepsMap) {
            const name = step.name?.toLowerCase();
            if (name) {
                if (stepNames.has(name)) {
                    result.warnings.push({
                        code: 'DUPLICATE_STEP_NAME',
                        message: `Duplicate step name: "${step.name}"`,
                        stepId: stepId,
                        severity: 'warning'
                    });
                }
                stepNames.set(name, stepId);
            }
        }
    }

    /**
     * Validate a single step
     */
    validateStep(result, step, stepsMap, connectionsMap) {
        const stepErrors = [];
        const stepWarnings = [];

        // Validate step has required fields
        if (!step.id) {
            stepErrors.push({ code: 'MISSING_ID', message: 'Step is missing an ID' });
        }

        if (!step.name || step.name.trim() === '') {
            stepErrors.push({ code: 'MISSING_NAME', message: 'Step must have a name' });
        }

        if (!step.type) {
            stepErrors.push({ code: 'MISSING_TYPE', message: 'Step must have a type' });
        }

        // Validate processor-specific configuration
        if (step.type === 'PROCESSOR' && step.processor) {
            const processorName = step.processor.processorName;

            // Check processor exists in metadata
            const metadata = this.processorMetadata.get(processorName);
            if (metadata) {
                // Validate required fields
                const requiredFields = (metadata.inputFields || []).filter(f => f.required);
                for (const field of requiredFields) {
                    const value = step.inputConfig?.[field.name];
                    if (this.isEmpty(value)) {
                        stepErrors.push({
                            code: 'MISSING_REQUIRED_FIELD',
                            message: `Required field "${field.displayName || field.name}" is not configured`,
                            field: field.name
                        });
                    }
                }

                // Run processor-specific validator
                const validator = this.processorValidators[processorName];
                if (validator) {
                    const processorErrors = validator(step, stepsMap, connectionsMap);
                    stepErrors.push(...processorErrors.errors || []);
                    stepWarnings.push(...processorErrors.warnings || []);
                }
            } else {
                stepWarnings.push({
                    code: 'UNKNOWN_PROCESSOR',
                    message: `Processor "${processorName}" is not recognized`
                });
            }
        }

        // Validate step position
        if (!step.position || step.position.x === undefined || step.position.y === undefined) {
            stepWarnings.push({ code: 'MISSING_POSITION', message: 'Step is missing position data' });
        }

        // Store step-specific errors
        if (stepErrors.length > 0 || stepWarnings.length > 0) {
            result.stepErrors.set(step.id, { errors: stepErrors, warnings: stepWarnings });
            result.errors.push(...stepErrors.map(e => ({ ...e, stepId: step.id, stepName: step.name })));
            result.warnings.push(...stepWarnings.map(w => ({ ...w, stepId: step.id, stepName: step.name })));
        }
    }

    /**
     * Validate a connection
     */
    validateConnection(result, conn, stepsMap) {
        const connErrors = [];

        // Validate source and target exist
        if (!conn.sourceStepId) {
            connErrors.push({ code: 'MISSING_SOURCE', message: 'Connection is missing source step' });
        } else if (!stepsMap.has(conn.sourceStepId)) {
            connErrors.push({ code: 'INVALID_SOURCE', message: 'Connection source step does not exist' });
        }

        if (!conn.targetStepId) {
            connErrors.push({ code: 'MISSING_TARGET', message: 'Connection is missing target step' });
        } else if (!stepsMap.has(conn.targetStepId)) {
            connErrors.push({ code: 'INVALID_TARGET', message: 'Connection target step does not exist' });
        }

        // Don't allow self-connections
        if (conn.sourceStepId === conn.targetStepId) {
            connErrors.push({ code: 'SELF_CONNECTION', message: 'Step cannot connect to itself' });
        }

        // Validate connection type compatibility
        if (conn.sourceStepId && conn.targetStepId && stepsMap.has(conn.sourceStepId) && stepsMap.has(conn.targetStepId)) {
            const sourceStep = stepsMap.get(conn.sourceStepId);
            const targetStep = stepsMap.get(conn.targetStepId);
            const connType = conn.type || 'output';

            const sourceRules = this.connectionRules[sourceStep.type] || this.connectionRules.PROCESSOR;
            const targetRules = this.connectionRules[targetStep.type] || this.connectionRules.PROCESSOR;

            // Check if source can have this output type
            const normalizedType = this.normalizeConnectionType(connType);
            if (sourceRules.allowedOutputTypes && !sourceRules.allowedOutputTypes.includes(normalizedType)) {
                connErrors.push({
                    code: 'INVALID_OUTPUT_TYPE',
                    message: `Step "${sourceStep.name}" cannot have ${connType} output connections`
                });
            }

            // Check if target can receive this input type
            if (targetRules.allowedInputTypes && !targetRules.allowedInputTypes.includes(normalizedType)) {
                connErrors.push({
                    code: 'INVALID_INPUT_TYPE',
                    message: `Step "${targetStep.name}" cannot receive ${connType} input connections`
                });
            }

            // Validate conditional connections have conditions
            if (normalizedType === 'CONDITIONAL' && !conn.condition?.expression) {
                connErrors.push({
                    code: 'MISSING_CONDITION',
                    message: 'Conditional connection must have a condition expression'
                });
            }
        }

        if (connErrors.length > 0) {
            result.connectionErrors.set(conn.id, connErrors);
            result.errors.push(...connErrors.map(e => ({ ...e, connectionId: conn.id })));
        }
    }

    /**
     * Normalize connection type to standard enum
     */
    normalizeConnectionType(type) {
        const typeMap = {
            'output': 'SEQUENTIAL',
            'error': 'ERROR',
            'conditional': 'CONDITIONAL',
            'timeout': 'TIMEOUT',
            'parallel': 'PARALLEL',
            'loop_back': 'LOOP_BACK',
            'loop_exit': 'LOOP_EXIT'
        };
        return typeMap[type?.toLowerCase()] || type?.toUpperCase() || 'SEQUENTIAL';
    }

    /**
     * Validate workflow flow
     */
    validateWorkflowFlow(result, stepsMap, connectionsMap, startStepId) {
        if (!startStepId || stepsMap.size === 0) return;

        // Build adjacency list
        const outgoing = new Map();
        const incoming = new Map();

        for (const [stepId] of stepsMap) {
            outgoing.set(stepId, []);
            incoming.set(stepId, []);
        }

        for (const [connId, conn] of connectionsMap) {
            if (conn.sourceStepId && conn.targetStepId) {
                outgoing.get(conn.sourceStepId)?.push(conn.targetStepId);
                incoming.get(conn.targetStepId)?.push(conn.sourceStepId);
            }
        }

        // Check for unreachable steps (no path from start)
        const reachable = new Set();
        const queue = [startStepId];
        reachable.add(startStepId);

        while (queue.length > 0) {
            const current = queue.shift();
            const neighbors = outgoing.get(current) || [];
            for (const neighbor of neighbors) {
                if (!reachable.has(neighbor)) {
                    reachable.add(neighbor);
                    queue.push(neighbor);
                }
            }
        }

        for (const [stepId, step] of stepsMap) {
            if (stepId !== startStepId && !reachable.has(stepId)) {
                result.warnings.push({
                    code: 'UNREACHABLE_STEP',
                    message: `Step "${step.name}" is not reachable from the start step`,
                    stepId: stepId,
                    stepName: step.name,
                    severity: 'warning'
                });
            }
        }

        // Check for orphan steps (no incoming connections except start)
        for (const [stepId, step] of stepsMap) {
            if (stepId !== startStepId && (incoming.get(stepId)?.length || 0) === 0) {
                result.warnings.push({
                    code: 'ORPHAN_STEP',
                    message: `Step "${step.name}" has no incoming connections`,
                    stepId: stepId,
                    stepName: step.name,
                    severity: 'warning'
                });
            }
        }

        // Check for dead ends (no outgoing connections for non-terminal steps)
        for (const [stepId, step] of stepsMap) {
            const rules = this.connectionRules[step.type] || this.connectionRules.PROCESSOR;
            const outputs = outgoing.get(stepId) || [];

            if (rules.requiresOutput && outputs.length === 0) {
                result.errors.push({
                    code: 'DEAD_END',
                    message: `Step "${step.name}" requires at least one outgoing connection`,
                    stepId: stepId,
                    stepName: step.name,
                    severity: 'error'
                });
            }

            if (rules.minOutputConnections && outputs.length < rules.minOutputConnections) {
                result.errors.push({
                    code: 'INSUFFICIENT_OUTPUTS',
                    message: `Step "${step.name}" requires at least ${rules.minOutputConnections} outgoing connections`,
                    stepId: stepId,
                    stepName: step.name,
                    severity: 'error'
                });
            }
        }

        // Detect simple cycles (potential infinite loops) - warning only
        const visited = new Set();
        const recursionStack = new Set();

        const detectCycle = (stepId, path = []) => {
            visited.add(stepId);
            recursionStack.add(stepId);
            path.push(stepId);

            const neighbors = outgoing.get(stepId) || [];
            for (const neighbor of neighbors) {
                if (!visited.has(neighbor)) {
                    const cycle = detectCycle(neighbor, [...path]);
                    if (cycle) return cycle;
                } else if (recursionStack.has(neighbor)) {
                    // Found a cycle
                    const cycleStart = path.indexOf(neighbor);
                    return path.slice(cycleStart);
                }
            }

            recursionStack.delete(stepId);
            return null;
        };

        const cycle = detectCycle(startStepId);
        if (cycle) {
            const cycleSteps = cycle.map(id => stepsMap.get(id)?.name || id).join(' -> ');
            result.warnings.push({
                code: 'POTENTIAL_CYCLE',
                message: `Potential infinite loop detected: ${cycleSteps}`,
                steps: cycle,
                severity: 'warning'
            });
        }
    }

    /**
     * Validate expression references
     */
    validateExpressionReferences(result, steps, connectionsMap) {
        const stepOutputs = new Map();

        // Build available outputs from each step
        for (const step of steps) {
            stepOutputs.set(step.id, step.name);
        }

        // Check expression references in step configurations
        for (const step of steps) {
            if (!step.inputConfig) continue;

            for (const [fieldName, value] of Object.entries(step.inputConfig)) {
                if (typeof value === 'string') {
                    const references = this.extractReferences(value);

                    for (const ref of references) {
                        // Check step references
                        if (ref.type === 'step') {
                            const referencedStep = Array.from(stepOutputs.entries())
                                .find(([id, name]) => name === ref.stepName);

                            if (!referencedStep) {
                                result.warnings.push({
                                    code: 'INVALID_STEP_REFERENCE',
                                    message: `Step "${step.name}" references unknown step "${ref.stepName}" in field "${fieldName}"`,
                                    stepId: step.id,
                                    stepName: step.name,
                                    field: fieldName,
                                    severity: 'warning'
                                });
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract variable/step references from an expression
     */
    extractReferences(expression) {
        const references = [];

        // Match ${stepName.output.field} pattern
        const stepPattern = /\$\{([^.}]+)\.output\.([^}]+)\}/g;
        let match;
        while ((match = stepPattern.exec(expression)) !== null) {
            references.push({
                type: 'step',
                stepName: match[1],
                field: match[2]
            });
        }

        // Match ${context.varName} pattern
        const contextPattern = /\$\{context\.([^}]+)\}/g;
        while ((match = contextPattern.exec(expression)) !== null) {
            references.push({
                type: 'context',
                varName: match[1]
            });
        }

        return references;
    }

    // ============================================
    // Processor-Specific Validators
    // ============================================

    validateIfCondition(step, stepsMap, connectionsMap) {
        const errors = [];
        const warnings = [];

        const condition = step.inputConfig?.condition;
        if (!condition) {
            errors.push({
                code: 'MISSING_CONDITION',
                message: 'If Condition requires a condition expression'
            });
        } else {
            // Validate condition syntax
            const syntaxError = this.validateConditionSyntax(condition);
            if (syntaxError) {
                errors.push({
                    code: 'INVALID_CONDITION_SYNTAX',
                    message: syntaxError
                });
            }
        }

        // Should have at least true and false branches
        const outgoingConns = Array.from(connectionsMap.values())
            .filter(c => c.sourceStepId === step.id);

        if (outgoingConns.length < 2) {
            warnings.push({
                code: 'INCOMPLETE_BRANCHES',
                message: 'If Condition should have both true and false branches'
            });
        }

        return { errors, warnings };
    }

    validateSwitch(step, stepsMap, connectionsMap) {
        const errors = [];
        const warnings = [];

        const expression = step.inputConfig?.expression;
        if (!expression) {
            errors.push({
                code: 'MISSING_EXPRESSION',
                message: 'Switch requires an expression to evaluate'
            });
        }

        const cases = step.inputConfig?.cases;
        if (!cases || (Array.isArray(cases) && cases.length === 0)) {
            errors.push({
                code: 'NO_CASES',
                message: 'Switch must have at least one case defined'
            });
        }

        // Check for duplicate case values
        if (Array.isArray(cases)) {
            const values = cases.map(c => c.value);
            const duplicates = values.filter((v, i) => values.indexOf(v) !== i);
            if (duplicates.length > 0) {
                errors.push({
                    code: 'DUPLICATE_CASES',
                    message: `Duplicate case values: ${duplicates.join(', ')}`
                });
            }
        }

        return { errors, warnings };
    }

    validateForEach(step, stepsMap, connectionsMap) {
        const errors = [];
        const warnings = [];

        const collection = step.inputConfig?.collection;
        if (!collection) {
            errors.push({
                code: 'MISSING_COLLECTION',
                message: 'ForEach requires a collection to iterate over'
            });
        }

        const itemVariable = step.inputConfig?.itemVariable;
        if (!itemVariable) {
            warnings.push({
                code: 'MISSING_ITEM_VARIABLE',
                message: 'ForEach should define an item variable name'
            });
        }

        return { errors, warnings };
    }

    validateHttpRequest(step, stepsMap, connectionsMap) {
        const errors = [];
        const warnings = [];

        const url = step.inputConfig?.url;
        if (!url) {
            errors.push({
                code: 'MISSING_URL',
                message: 'HTTP Request requires a URL'
            });
        } else if (!this.isValidUrlOrExpression(url)) {
            errors.push({
                code: 'INVALID_URL',
                message: 'URL is not valid'
            });
        }

        const method = step.inputConfig?.method;
        if (!method) {
            warnings.push({
                code: 'MISSING_METHOD',
                message: 'HTTP method not specified, will default to GET'
            });
        }

        // Validate body for POST/PUT/PATCH
        if (['POST', 'PUT', 'PATCH'].includes(method?.toUpperCase())) {
            const body = step.inputConfig?.body;
            if (!body && !step.inputConfig?.bodyTemplate) {
                warnings.push({
                    code: 'MISSING_BODY',
                    message: `${method} request typically requires a body`
                });
            }
        }

        return { errors, warnings };
    }

    validateJavaScript(step, stepsMap, connectionsMap) {
        const errors = [];
        const warnings = [];

        const script = step.inputConfig?.script;
        if (!script) {
            errors.push({
                code: 'MISSING_SCRIPT',
                message: 'JavaScript processor requires a script'
            });
        } else {
            // Basic syntax check
            try {
                new Function(script);
            } catch (e) {
                errors.push({
                    code: 'INVALID_SCRIPT_SYNTAX',
                    message: `Script syntax error: ${e.message}`
                });
            }
        }

        return { errors, warnings };
    }

    validateSqlQuery(step, stepsMap, connectionsMap) {
        const errors = [];
        const warnings = [];

        const query = step.inputConfig?.query;
        if (!query) {
            errors.push({
                code: 'MISSING_QUERY',
                message: 'SQL Query processor requires a query'
            });
        } else {
            // Check for potential SQL injection (parameters should use placeholders)
            if (/\$\{[^}]+\}/.test(query) && !/\?\s*$/.test(query)) {
                warnings.push({
                    code: 'POTENTIAL_SQL_INJECTION',
                    message: 'Consider using parameterized queries instead of string interpolation'
                });
            }
        }

        const dataSource = step.inputConfig?.dataSource;
        if (!dataSource) {
            errors.push({
                code: 'MISSING_DATASOURCE',
                message: 'SQL Query requires a data source configuration'
            });
        }

        return { errors, warnings };
    }

    validateMongoQuery(step, stepsMap, connectionsMap) {
        const errors = [];
        const warnings = [];

        const collection = step.inputConfig?.collection;
        if (!collection) {
            errors.push({
                code: 'MISSING_COLLECTION',
                message: 'MongoDB Query requires a collection name'
            });
        }

        const operation = step.inputConfig?.operation;
        if (!operation) {
            errors.push({
                code: 'MISSING_OPERATION',
                message: 'MongoDB Query requires an operation type'
            });
        }

        // Validate query/filter is valid JSON
        const query = step.inputConfig?.query || step.inputConfig?.filter;
        if (query && typeof query === 'string') {
            try {
                JSON.parse(query);
            } catch (e) {
                errors.push({
                    code: 'INVALID_QUERY_JSON',
                    message: 'Query must be valid JSON'
                });
            }
        }

        return { errors, warnings };
    }

    validateEmailSend(step, stepsMap, connectionsMap) {
        const errors = [];
        const warnings = [];

        const to = step.inputConfig?.to;
        if (!to) {
            errors.push({
                code: 'MISSING_RECIPIENT',
                message: 'Email requires at least one recipient'
            });
        } else if (!this.isValidEmailOrExpression(to)) {
            errors.push({
                code: 'INVALID_EMAIL',
                message: 'Invalid recipient email address'
            });
        }

        const subject = step.inputConfig?.subject;
        if (!subject) {
            warnings.push({
                code: 'MISSING_SUBJECT',
                message: 'Email should have a subject'
            });
        }

        const body = step.inputConfig?.body || step.inputConfig?.bodyTemplate;
        if (!body) {
            warnings.push({
                code: 'MISSING_BODY',
                message: 'Email should have a body'
            });
        }

        return { errors, warnings };
    }

    validateSetVariable(step, stepsMap, connectionsMap) {
        const errors = [];
        const warnings = [];

        const variableName = step.inputConfig?.variableName || step.inputConfig?.name;
        if (!variableName) {
            errors.push({
                code: 'MISSING_VARIABLE_NAME',
                message: 'Set Variable requires a variable name'
            });
        } else if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(variableName)) {
            errors.push({
                code: 'INVALID_VARIABLE_NAME',
                message: 'Variable name must start with a letter or underscore and contain only alphanumeric characters'
            });
        }

        const value = step.inputConfig?.value;
        if (value === undefined || value === null || value === '') {
            warnings.push({
                code: 'EMPTY_VALUE',
                message: 'Variable value is empty'
            });
        }

        return { errors, warnings };
    }

    // ============================================
    // Utility Methods
    // ============================================

    isEmpty(value) {
        if (value === null || value === undefined) return true;
        if (typeof value === 'string' && value.trim() === '') return true;
        if (Array.isArray(value) && value.length === 0) return true;
        if (typeof value === 'object' && Object.keys(value).length === 0) return true;
        return false;
    }

    isValidUrlOrExpression(value) {
        // Allow expressions
        if (/\$\{[^}]+\}/.test(value) || /#\{[^}]+\}/.test(value)) {
            return true;
        }
        // Validate as URL
        try {
            new URL(value);
            return true;
        } catch {
            return false;
        }
    }

    isValidEmailOrExpression(value) {
        // Allow expressions
        if (/\$\{[^}]+\}/.test(value) || /#\{[^}]+\}/.test(value)) {
            return true;
        }
        // Basic email validation
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
    }

    validateConditionSyntax(condition) {
        // Basic condition syntax validation
        if (!condition) return 'Condition is empty';

        // Check for balanced parentheses
        let parenCount = 0;
        for (const char of condition) {
            if (char === '(') parenCount++;
            if (char === ')') parenCount--;
            if (parenCount < 0) return 'Unmatched closing parenthesis';
        }
        if (parenCount !== 0) return 'Unmatched opening parenthesis';

        // Check for common operators
        const hasOperator = /[=<>!&|]/.test(condition) ||
            /\b(and|or|not|eq|ne|lt|gt|le|ge)\b/i.test(condition);

        if (!hasOperator && !condition.includes('${') && !condition.includes('#{')) {
            return 'Condition should contain a comparison or logical operator';
        }

        return null;
    }

    /**
     * Get validation summary for display
     */
    getSummary(result) {
        return {
            valid: result.valid,
            errorCount: result.errors.length,
            warningCount: result.warnings.length,
            stepErrorCount: result.stepErrors.size,
            connectionErrorCount: result.connectionErrors.size
        };
    }

    /**
     * Get errors for a specific step
     */
    getStepErrors(result, stepId) {
        return result.stepErrors.get(stepId) || { errors: [], warnings: [] };
    }

    /**
     * Get errors for a specific connection
     */
    getConnectionErrors(result, connectionId) {
        return result.connectionErrors.get(connectionId) || [];
    }
}

// Global instance
const workflowValidator = new WorkflowValidator();
