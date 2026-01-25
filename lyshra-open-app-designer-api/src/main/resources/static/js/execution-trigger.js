/**
 * Execution Trigger for Lyshra OpenApp Designer
 * Provides UI for manually triggering workflow executions with:
 * - Workflow and version selection
 * - Input payload editor with JSON validation
 * - Variables and metadata configuration
 * - Execution confirmation with preview
 */

class ExecutionTrigger {
    constructor(options = {}) {
        this.workflows = [];
        this.versions = [];
        this.selectedWorkflow = null;
        this.selectedVersion = null;
        this.onExecutionStarted = options.onExecutionStarted || (() => {});
        this.container = null;
        this.jsonEditor = null;
    }

    /**
     * Initialize the execution trigger
     */
    async init(container) {
        this.container = container;
        await this.loadWorkflows();
        this.render();
    }

    /**
     * Load available workflows
     */
    async loadWorkflows() {
        try {
            const allWorkflows = await api.getWorkflows();
            // Only show active workflows
            this.workflows = allWorkflows.filter(w =>
                w.lifecycleState === 'ACTIVE' || w.lifecycleState === 'DRAFT'
            );
        } catch (error) {
            console.error('Failed to load workflows:', error);
            this.workflows = [];
        }
    }

    /**
     * Load versions for selected workflow
     */
    async loadVersions(workflowId) {
        try {
            const versions = await api.getVersions(workflowId);
            // Only show active and draft versions
            this.versions = versions.filter(v =>
                v.state === 'ACTIVE' || v.state === 'DRAFT'
            );
            // Sort by state (ACTIVE first) then by creation date
            this.versions.sort((a, b) => {
                if (a.state === 'ACTIVE' && b.state !== 'ACTIVE') return -1;
                if (a.state !== 'ACTIVE' && b.state === 'ACTIVE') return 1;
                return new Date(b.createdAt) - new Date(a.createdAt);
            });
        } catch (error) {
            console.error('Failed to load versions:', error);
            this.versions = [];
        }
    }

    /**
     * Render the execution trigger UI
     */
    render() {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="execution-trigger">
                <form id="executionTriggerForm" novalidate>
                    <!-- Step 1: Workflow Selection -->
                    <div class="card mb-4">
                        <div class="card-header">
                            <span class="step-number">1</span>
                            Select Workflow
                        </div>
                        <div class="card-body">
                            <div class="mb-3">
                                <label class="form-label">Workflow *</label>
                                <select class="form-select" id="selectWorkflow" required>
                                    <option value="">-- Select a workflow --</option>
                                    ${this.workflows.map(w => `
                                        <option value="${w.id}" data-state="${w.lifecycleState}">
                                            ${escapeHtml(w.name)} (${w.lifecycleState})
                                        </option>
                                    `).join('')}
                                </select>
                                <div class="invalid-feedback">Please select a workflow</div>
                            </div>

                            <div id="workflowInfo" class="workflow-info d-none">
                                <!-- Workflow info displayed here -->
                            </div>
                        </div>
                    </div>

                    <!-- Step 2: Version Selection -->
                    <div class="card mb-4" id="versionSelectionCard">
                        <div class="card-header">
                            <span class="step-number">2</span>
                            Select Version
                        </div>
                        <div class="card-body">
                            <div id="versionSelector">
                                <p class="text-muted">Select a workflow first to see available versions</p>
                            </div>
                        </div>
                    </div>

                    <!-- Step 3: Input Configuration -->
                    <div class="card mb-4" id="inputConfigCard">
                        <div class="card-header d-flex justify-content-between align-items-center">
                            <div>
                                <span class="step-number">3</span>
                                Configure Input
                            </div>
                            <div class="btn-group btn-group-sm">
                                <button type="button" class="btn btn-outline-secondary" onclick="executionTrigger.formatJson()">
                                    <i class="bi bi-code"></i> Format
                                </button>
                                <button type="button" class="btn btn-outline-secondary" onclick="executionTrigger.validateJson()">
                                    <i class="bi bi-check-circle"></i> Validate
                                </button>
                                <button type="button" class="btn btn-outline-secondary" onclick="executionTrigger.loadSampleInput()">
                                    <i class="bi bi-file-earmark-code"></i> Sample
                                </button>
                            </div>
                        </div>
                        <div class="card-body">
                            <!-- Tabs for input types -->
                            <ul class="nav nav-tabs mb-3" role="tablist">
                                <li class="nav-item">
                                    <a class="nav-link active" data-bs-toggle="tab" href="#inputDataTab">
                                        <i class="bi bi-braces"></i> Input Data
                                    </a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link" data-bs-toggle="tab" href="#variablesTab">
                                        <i class="bi bi-collection"></i> Variables
                                    </a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link" data-bs-toggle="tab" href="#metadataTab">
                                        <i class="bi bi-tags"></i> Metadata
                                    </a>
                                </li>
                            </ul>

                            <div class="tab-content">
                                <!-- Input Data Tab -->
                                <div class="tab-pane fade show active" id="inputDataTab">
                                    <div class="mb-2">
                                        <label class="form-label">Input Data (JSON)</label>
                                        <div class="input-help text-muted small mb-2">
                                            Provide the initial input data for the workflow execution as a JSON object.
                                        </div>
                                    </div>
                                    <div class="json-editor-container">
                                        <textarea class="form-control code-editor" id="inputDataEditor"
                                                  rows="10" placeholder='{\n  "key": "value"\n}'>{}</textarea>
                                        <div id="inputDataError" class="json-error d-none"></div>
                                    </div>
                                </div>

                                <!-- Variables Tab -->
                                <div class="tab-pane fade" id="variablesTab">
                                    <div class="mb-2">
                                        <label class="form-label">Initial Variables (JSON)</label>
                                        <div class="input-help text-muted small mb-2">
                                            Pre-set workflow variables that will be available at the start of execution.
                                        </div>
                                    </div>
                                    <div class="json-editor-container">
                                        <textarea class="form-control code-editor" id="variablesEditor"
                                                  rows="8" placeholder='{\n  "variableName": "value"\n}'>{}</textarea>
                                        <div id="variablesError" class="json-error d-none"></div>
                                    </div>
                                </div>

                                <!-- Metadata Tab -->
                                <div class="tab-pane fade" id="metadataTab">
                                    <div class="mb-3">
                                        <label class="form-label">Correlation ID</label>
                                        <input type="text" class="form-control" id="correlationId"
                                               placeholder="Optional unique identifier for tracking">
                                        <div class="form-text">Use to correlate this execution with external systems</div>
                                    </div>
                                    <div class="mb-2">
                                        <label class="form-label">Metadata (JSON)</label>
                                        <div class="input-help text-muted small mb-2">
                                            Additional metadata for the execution (tags, labels, etc.)
                                        </div>
                                    </div>
                                    <div class="json-editor-container">
                                        <textarea class="form-control code-editor" id="metadataEditor"
                                                  rows="6" placeholder='{\n  "environment": "production"\n}'>{}</textarea>
                                        <div id="metadataError" class="json-error d-none"></div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Step 4: Review & Execute -->
                    <div class="card mb-4">
                        <div class="card-header">
                            <span class="step-number">4</span>
                            Review & Execute
                        </div>
                        <div class="card-body">
                            <div id="executionPreview" class="execution-preview mb-3">
                                <p class="text-muted">Complete the steps above to see execution preview</p>
                            </div>

                            <div class="d-flex justify-content-between align-items-center">
                                <div class="form-check">
                                    <input class="form-check-input" type="checkbox" id="confirmExecution">
                                    <label class="form-check-label" for="confirmExecution">
                                        I confirm I want to execute this workflow
                                    </label>
                                </div>
                                <button type="submit" class="btn btn-success btn-lg" id="btnExecute" disabled>
                                    <i class="bi bi-play-fill"></i> Start Execution
                                </button>
                            </div>
                        </div>
                    </div>
                </form>
            </div>

            ${this.renderConfirmationModal()}
        `;

        this.attachEventListeners();
    }

    /**
     * Render confirmation modal
     */
    renderConfirmationModal() {
        return `
            <div class="modal fade" id="confirmExecutionModal" tabindex="-1">
                <div class="modal-dialog modal-lg">
                    <div class="modal-content">
                        <div class="modal-header bg-success text-white">
                            <h5 class="modal-title"><i class="bi bi-play-circle"></i> Confirm Execution</h5>
                            <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body">
                            <div class="alert alert-info">
                                <i class="bi bi-info-circle"></i>
                                Please review the execution details below before confirming.
                            </div>

                            <div id="confirmationDetails">
                                <!-- Details loaded dynamically -->
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                            <button type="button" class="btn btn-success" id="btnConfirmExecute">
                                <i class="bi bi-play-fill"></i> Confirm & Execute
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Attach event listeners
     */
    attachEventListeners() {
        // Workflow selection
        const workflowSelect = document.getElementById('selectWorkflow');
        workflowSelect?.addEventListener('change', (e) => this.onWorkflowChange(e.target.value));

        // Confirmation checkbox
        const confirmCheckbox = document.getElementById('confirmExecution');
        confirmCheckbox?.addEventListener('change', (e) => this.updateExecuteButton());

        // Form submission
        const form = document.getElementById('executionTriggerForm');
        form?.addEventListener('submit', (e) => {
            e.preventDefault();
            this.showConfirmationModal();
        });

        // Confirm execution button
        document.getElementById('btnConfirmExecute')?.addEventListener('click', () => {
            this.executeWorkflow();
        });

        // JSON editor validation on blur
        ['inputDataEditor', 'variablesEditor', 'metadataEditor'].forEach(id => {
            const editor = document.getElementById(id);
            editor?.addEventListener('blur', () => this.validateJsonField(id));
            editor?.addEventListener('input', () => this.updatePreview());
        });

        // Correlation ID change
        document.getElementById('correlationId')?.addEventListener('input', () => this.updatePreview());
    }

    /**
     * Handle workflow selection change
     */
    async onWorkflowChange(workflowId) {
        this.selectedWorkflow = this.workflows.find(w => w.id === workflowId);
        this.selectedVersion = null;

        const infoDiv = document.getElementById('workflowInfo');
        const versionSelector = document.getElementById('versionSelector');

        if (!this.selectedWorkflow) {
            infoDiv.classList.add('d-none');
            versionSelector.innerHTML = '<p class="text-muted">Select a workflow first to see available versions</p>';
            this.updatePreview();
            return;
        }

        // Show workflow info
        infoDiv.classList.remove('d-none');
        infoDiv.innerHTML = `
            <div class="d-flex align-items-start gap-3">
                <div class="workflow-icon">
                    <i class="bi bi-diagram-3 display-6 text-primary"></i>
                </div>
                <div>
                    <h6 class="mb-1">${escapeHtml(this.selectedWorkflow.name)}</h6>
                    <p class="text-muted small mb-1">${this.selectedWorkflow.description || 'No description'}</p>
                    <div class="small">
                        <span class="badge ${getStatusBadgeClass(this.selectedWorkflow.lifecycleState)}">
                            ${this.selectedWorkflow.lifecycleState}
                        </span>
                        <span class="text-muted ms-2">
                            <i class="bi bi-folder"></i> ${this.selectedWorkflow.organization}/${this.selectedWorkflow.module}
                        </span>
                    </div>
                </div>
            </div>
        `;

        // Load versions
        versionSelector.innerHTML = '<div class="text-center py-3"><div class="spinner-border spinner-border-sm"></div> Loading versions...</div>';
        await this.loadVersions(workflowId);
        this.renderVersionSelector();
        this.updatePreview();
    }

    /**
     * Render version selector
     */
    renderVersionSelector() {
        const container = document.getElementById('versionSelector');

        if (this.versions.length === 0) {
            container.innerHTML = `
                <div class="alert alert-warning">
                    <i class="bi bi-exclamation-triangle"></i>
                    No active or draft versions available for this workflow.
                </div>
            `;
            return;
        }

        const activeVersion = this.versions.find(v => v.state === 'ACTIVE');

        container.innerHTML = `
            <div class="version-options">
                ${this.versions.map(v => `
                    <div class="version-option ${v.state === 'ACTIVE' ? 'recommended' : ''}"
                         data-version-id="${v.id}">
                        <div class="version-radio">
                            <input type="radio" name="versionSelect" id="version_${v.id}"
                                   value="${v.id}" ${v.state === 'ACTIVE' ? 'checked' : ''}>
                        </div>
                        <div class="version-details">
                            <div class="d-flex align-items-center gap-2">
                                <strong>${escapeHtml(v.versionNumber)}</strong>
                                <span class="badge ${getStatusBadgeClass(v.state)}">${v.state}</span>
                                ${v.state === 'ACTIVE' ? '<span class="badge bg-primary">Recommended</span>' : ''}
                            </div>
                            <div class="small text-muted mt-1">
                                ${v.description || 'No description'}
                            </div>
                            <div class="small text-muted">
                                <i class="bi bi-diagram-3"></i> ${v.steps?.length || 0} steps |
                                <i class="bi bi-calendar"></i> ${formatRelativeTime(v.createdAt)}
                            </div>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;

        // Attach click handlers
        container.querySelectorAll('.version-option').forEach(option => {
            option.addEventListener('click', () => {
                const radio = option.querySelector('input[type="radio"]');
                radio.checked = true;
                this.onVersionChange(radio.value);

                // Update visual selection
                container.querySelectorAll('.version-option').forEach(o => o.classList.remove('selected'));
                option.classList.add('selected');
            });
        });

        // Select active version by default
        if (activeVersion) {
            this.onVersionChange(activeVersion.id);
            container.querySelector(`[data-version-id="${activeVersion.id}"]`)?.classList.add('selected');
        }
    }

    /**
     * Handle version selection change
     */
    onVersionChange(versionId) {
        this.selectedVersion = this.versions.find(v => v.id === versionId);
        this.updatePreview();
    }

    /**
     * Validate a JSON field
     */
    validateJsonField(fieldId) {
        const editor = document.getElementById(fieldId);
        const errorDiv = document.getElementById(fieldId.replace('Editor', 'Error'));

        if (!editor || !errorDiv) return true;

        const value = editor.value.trim();

        if (!value || value === '{}') {
            errorDiv.classList.add('d-none');
            editor.classList.remove('is-invalid');
            return true;
        }

        try {
            JSON.parse(value);
            errorDiv.classList.add('d-none');
            editor.classList.remove('is-invalid');
            return true;
        } catch (e) {
            errorDiv.textContent = `Invalid JSON: ${e.message}`;
            errorDiv.classList.remove('d-none');
            editor.classList.add('is-invalid');
            return false;
        }
    }

    /**
     * Validate all JSON fields
     */
    validateJson() {
        const fields = ['inputDataEditor', 'variablesEditor', 'metadataEditor'];
        let allValid = true;

        fields.forEach(fieldId => {
            if (!this.validateJsonField(fieldId)) {
                allValid = false;
            }
        });

        if (allValid) {
            this.showToast('All JSON fields are valid', 'success');
        }

        return allValid;
    }

    /**
     * Format JSON in editor
     */
    formatJson() {
        const activeTab = document.querySelector('.tab-pane.active');
        const editor = activeTab?.querySelector('textarea');

        if (!editor) return;

        try {
            const json = JSON.parse(editor.value || '{}');
            editor.value = JSON.stringify(json, null, 2);
            this.validateJsonField(editor.id);
            this.showToast('JSON formatted', 'success');
        } catch (e) {
            this.showToast('Invalid JSON - cannot format', 'error');
        }
    }

    /**
     * Load sample input based on workflow
     */
    loadSampleInput() {
        const editor = document.getElementById('inputDataEditor');
        if (!editor) return;

        const sample = {
            "orderId": "ORD-12345",
            "customer": {
                "id": "CUST-001",
                "name": "John Doe",
                "email": "john@example.com"
            },
            "items": [
                { "productId": "PROD-001", "quantity": 2, "price": 29.99 },
                { "productId": "PROD-002", "quantity": 1, "price": 49.99 }
            ],
            "shippingAddress": {
                "street": "123 Main St",
                "city": "New York",
                "state": "NY",
                "zip": "10001"
            },
            "timestamp": new Date().toISOString()
        };

        editor.value = JSON.stringify(sample, null, 2);
        this.validateJsonField('inputDataEditor');
        this.updatePreview();
        this.showToast('Sample input loaded', 'info');
    }

    /**
     * Update execution preview
     */
    updatePreview() {
        const preview = document.getElementById('executionPreview');
        if (!preview) return;

        if (!this.selectedWorkflow || !this.selectedVersion) {
            preview.innerHTML = '<p class="text-muted">Complete the steps above to see execution preview</p>';
            this.updateExecuteButton();
            return;
        }

        const inputData = this.getJsonValue('inputDataEditor');
        const variables = this.getJsonValue('variablesEditor');
        const metadata = this.getJsonValue('metadataEditor');
        const correlationId = document.getElementById('correlationId')?.value || '';

        preview.innerHTML = `
            <div class="execution-summary">
                <div class="row">
                    <div class="col-md-6">
                        <h6 class="text-muted mb-2">Workflow</h6>
                        <p class="mb-1"><strong>${escapeHtml(this.selectedWorkflow.name)}</strong></p>
                        <p class="small text-muted mb-0">${this.selectedWorkflow.organization}/${this.selectedWorkflow.module}</p>
                    </div>
                    <div class="col-md-6">
                        <h6 class="text-muted mb-2">Version</h6>
                        <p class="mb-1">
                            <strong>${escapeHtml(this.selectedVersion.versionNumber)}</strong>
                            <span class="badge ${getStatusBadgeClass(this.selectedVersion.state)} ms-1">${this.selectedVersion.state}</span>
                        </p>
                        <p class="small text-muted mb-0">${this.selectedVersion.steps?.length || 0} steps</p>
                    </div>
                </div>
                <hr>
                <div class="row">
                    <div class="col-md-4">
                        <h6 class="text-muted mb-2">Input Data</h6>
                        <code class="small">${Object.keys(inputData).length} properties</code>
                    </div>
                    <div class="col-md-4">
                        <h6 class="text-muted mb-2">Variables</h6>
                        <code class="small">${Object.keys(variables).length} variables</code>
                    </div>
                    <div class="col-md-4">
                        <h6 class="text-muted mb-2">Correlation ID</h6>
                        <code class="small">${correlationId || '(auto-generated)'}</code>
                    </div>
                </div>
            </div>
        `;

        this.updateExecuteButton();
    }

    /**
     * Get JSON value from editor
     */
    getJsonValue(editorId) {
        const editor = document.getElementById(editorId);
        if (!editor) return {};

        try {
            return JSON.parse(editor.value || '{}');
        } catch {
            return {};
        }
    }

    /**
     * Update execute button state
     */
    updateExecuteButton() {
        const btn = document.getElementById('btnExecute');
        const checkbox = document.getElementById('confirmExecution');

        if (!btn) return;

        const isValid = this.selectedWorkflow &&
                       this.selectedVersion &&
                       checkbox?.checked &&
                       this.validateJson();

        btn.disabled = !isValid;
    }

    /**
     * Show confirmation modal
     */
    showConfirmationModal() {
        if (!this.validateJson()) {
            this.showToast('Please fix JSON validation errors before executing', 'error');
            return;
        }

        const inputData = this.getJsonValue('inputDataEditor');
        const variables = this.getJsonValue('variablesEditor');
        const metadata = this.getJsonValue('metadataEditor');
        const correlationId = document.getElementById('correlationId')?.value || '';

        const details = document.getElementById('confirmationDetails');
        details.innerHTML = `
            <table class="table table-sm">
                <tbody>
                    <tr>
                        <th style="width: 150px;">Workflow</th>
                        <td>
                            <strong>${escapeHtml(this.selectedWorkflow.name)}</strong>
                            <span class="badge ${getStatusBadgeClass(this.selectedWorkflow.lifecycleState)} ms-1">
                                ${this.selectedWorkflow.lifecycleState}
                            </span>
                        </td>
                    </tr>
                    <tr>
                        <th>Version</th>
                        <td>
                            <strong>${escapeHtml(this.selectedVersion.versionNumber)}</strong>
                            <span class="badge ${getStatusBadgeClass(this.selectedVersion.state)} ms-1">
                                ${this.selectedVersion.state}
                            </span>
                        </td>
                    </tr>
                    <tr>
                        <th>Correlation ID</th>
                        <td><code>${correlationId || '(auto-generated)'}</code></td>
                    </tr>
                </tbody>
            </table>

            <div class="accordion" id="executionDataAccordion">
                <div class="accordion-item">
                    <h2 class="accordion-header">
                        <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse"
                                data-bs-target="#inputDataCollapse">
                            Input Data (${Object.keys(inputData).length} properties)
                        </button>
                    </h2>
                    <div id="inputDataCollapse" class="accordion-collapse collapse" data-bs-parent="#executionDataAccordion">
                        <div class="accordion-body">
                            <pre class="code-block mb-0">${escapeHtml(JSON.stringify(inputData, null, 2))}</pre>
                        </div>
                    </div>
                </div>
                <div class="accordion-item">
                    <h2 class="accordion-header">
                        <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse"
                                data-bs-target="#variablesCollapse">
                            Variables (${Object.keys(variables).length} variables)
                        </button>
                    </h2>
                    <div id="variablesCollapse" class="accordion-collapse collapse" data-bs-parent="#executionDataAccordion">
                        <div class="accordion-body">
                            <pre class="code-block mb-0">${escapeHtml(JSON.stringify(variables, null, 2))}</pre>
                        </div>
                    </div>
                </div>
                ${Object.keys(metadata).length > 0 ? `
                    <div class="accordion-item">
                        <h2 class="accordion-header">
                            <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse"
                                    data-bs-target="#metadataCollapse">
                                Metadata (${Object.keys(metadata).length} entries)
                            </button>
                        </h2>
                        <div id="metadataCollapse" class="accordion-collapse collapse" data-bs-parent="#executionDataAccordion">
                            <div class="accordion-body">
                                <pre class="code-block mb-0">${escapeHtml(JSON.stringify(metadata, null, 2))}</pre>
                            </div>
                        </div>
                    </div>
                ` : ''}
            </div>
        `;

        const modal = new bootstrap.Modal(document.getElementById('confirmExecutionModal'));
        modal.show();
    }

    /**
     * Execute workflow
     */
    async executeWorkflow() {
        const btn = document.getElementById('btnConfirmExecute');
        const originalText = btn.innerHTML;

        try {
            btn.disabled = true;
            btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Starting...';

            const request = {
                inputData: this.getJsonValue('inputDataEditor'),
                variables: this.getJsonValue('variablesEditor'),
                metadata: this.getJsonValue('metadataEditor'),
                correlationId: document.getElementById('correlationId')?.value || null,
                versionId: this.selectedVersion.id
            };

            const execution = await api.startExecution(this.selectedWorkflow.id, request);

            // Close modal
            bootstrap.Modal.getInstance(document.getElementById('confirmExecutionModal')).hide();

            // Show success message
            this.showExecutionStartedMessage(execution);

            // Callback
            this.onExecutionStarted(execution);

        } catch (error) {
            console.error('Failed to start execution:', error);
            this.showToast(`Failed to start execution: ${error.message}`, 'error');
        } finally {
            btn.disabled = false;
            btn.innerHTML = originalText;
        }
    }

    /**
     * Show execution started message
     */
    showExecutionStartedMessage(execution) {
        const container = this.container;
        container.innerHTML = `
            <div class="execution-started text-center py-5">
                <div class="success-icon mb-4">
                    <i class="bi bi-check-circle-fill text-success" style="font-size: 5rem;"></i>
                </div>
                <h2 class="mb-3">Workflow Execution Started!</h2>
                <p class="text-muted mb-4">Your workflow is now running.</p>

                <div class="card mx-auto" style="max-width: 500px;">
                    <div class="card-body">
                        <table class="table table-sm mb-0">
                            <tr>
                                <th>Execution ID</th>
                                <td><code>${execution.id}</code></td>
                            </tr>
                            <tr>
                                <th>Status</th>
                                <td><span class="badge ${getStatusBadgeClass(execution.status)}">${execution.status}</span></td>
                            </tr>
                            <tr>
                                <th>Started At</th>
                                <td>${formatDate(execution.startedAt)}</td>
                            </tr>
                        </table>
                    </div>
                </div>

                <div class="mt-4">
                    <a href="/executions?id=${execution.id}" class="btn btn-primary">
                        <i class="bi bi-eye"></i> View Execution
                    </a>
                    <button class="btn btn-outline-secondary ms-2" onclick="executionTrigger.reset()">
                        <i class="bi bi-plus-circle"></i> Start Another
                    </button>
                </div>
            </div>
        `;
    }

    /**
     * Reset the form
     */
    reset() {
        this.selectedWorkflow = null;
        this.selectedVersion = null;
        this.render();
    }

    /**
     * Show toast notification
     */
    showToast(message, type = 'info') {
        let container = document.getElementById('toastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toastContainer';
            container.className = 'toast-container position-fixed bottom-0 end-0 p-3';
            document.body.appendChild(container);
        }

        const toastId = 'toast-' + Date.now();
        const bgClass = type === 'success' ? 'bg-success' :
                        type === 'warning' ? 'bg-warning text-dark' :
                        type === 'error' ? 'bg-danger' : 'bg-info';

        container.innerHTML += `
            <div id="${toastId}" class="toast ${bgClass} text-white" role="alert">
                <div class="toast-body d-flex align-items-center">
                    <i class="bi ${type === 'success' ? 'bi-check-circle' :
                                   type === 'warning' ? 'bi-exclamation-triangle' :
                                   type === 'error' ? 'bi-x-circle' : 'bi-info-circle'} me-2"></i>
                    ${message}
                    <button type="button" class="btn-close btn-close-white ms-auto" data-bs-dismiss="toast"></button>
                </div>
            </div>
        `;

        const toastEl = document.getElementById(toastId);
        const toast = new bootstrap.Toast(toastEl, { delay: 4000 });
        toast.show();

        toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
    }
}

// Global instance
let executionTrigger;
