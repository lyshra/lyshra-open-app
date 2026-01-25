/**
 * Workflow Designer for Lyshra OpenApp
 * Provides drag-and-drop workflow design capabilities
 */

class WorkflowDesigner {
    constructor() {
        this.canvas = document.getElementById('workflowCanvas');
        this.stepsLayer = document.getElementById('stepsLayer');
        this.connectionsLayer = document.getElementById('connectionsLayer');
        this.workflow = null;
        this.version = null;
        this.steps = new Map();
        this.connections = new Map();
        this.selectedStep = null;
        this.selectedConnection = null;
        this.draggedProcessor = null;
        this.connecting = false;
        this.connectionStart = null;
        this.zoom = 1;
        this.panX = 0;
        this.panY = 0;
        this.processors = [];

        this.init();
    }

    async init() {
        await this.loadProcessors();
        this.setupEventListeners();
        this.setupDragAndDrop();
    }

    async loadProcessors() {
        try {
            this.processors = await api.getProcessors();
            this.renderProcessorPalette();
        } catch (error) {
            console.error('Failed to load processors:', error);
        }
    }

    renderProcessorPalette() {
        const palette = document.getElementById('processorPalette');
        const categories = {};

        this.processors.forEach(proc => {
            if (!categories[proc.category]) {
                categories[proc.category] = [];
            }
            categories[proc.category].push(proc);
        });

        let html = '';
        for (const [category, procs] of Object.entries(categories)) {
            html += `
                <div class="processor-category">
                    <div class="processor-category-header" data-bs-toggle="collapse"
                         data-bs-target="#cat-${category.replace(/\s/g, '')}">
                        <i class="bi bi-chevron-down"></i> ${category}
                    </div>
                    <div class="collapse show" id="cat-${category.replace(/\s/g, '')}">
            `;
            procs.forEach(proc => {
                html += `
                    <div class="processor-item d-flex align-items-start"
                         draggable="true"
                         data-processor-id="${proc.identifier}">
                        <div class="processor-icon">
                            <i class="bi bi-${proc.icon || 'box'}"></i>
                        </div>
                        <div>
                            <div class="processor-name">${proc.displayName}</div>
                            <div class="processor-desc">${proc.description || ''}</div>
                        </div>
                    </div>
                `;
            });
            html += '</div></div>';
        }

        palette.innerHTML = html;

        palette.querySelectorAll('.processor-item').forEach(item => {
            item.addEventListener('dragstart', (e) => {
                this.draggedProcessor = this.processors.find(
                    p => p.identifier === item.dataset.processorId
                );
                e.dataTransfer.setData('text/plain', item.dataset.processorId);
                e.dataTransfer.effectAllowed = 'copy';
            });

            item.addEventListener('dragend', () => {
                this.draggedProcessor = null;
            });
        });

        const searchInput = document.getElementById('processorSearch');
        searchInput.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase();
            palette.querySelectorAll('.processor-item').forEach(item => {
                const name = item.querySelector('.processor-name').textContent.toLowerCase();
                const desc = item.querySelector('.processor-desc').textContent.toLowerCase();
                item.style.display = (name.includes(query) || desc.includes(query)) ? '' : 'none';
            });
        });
    }

    setupEventListeners() {
        document.getElementById('btnNew').addEventListener('click', () => this.newWorkflow());
        document.getElementById('btnSave').addEventListener('click', () => this.saveWorkflow());
        document.getElementById('btnValidate').addEventListener('click', () => this.validateWorkflow());
        document.getElementById('btnExecute').addEventListener('click', () => this.executeWorkflow());
        document.getElementById('btnZoomIn').addEventListener('click', () => this.zoomIn());
        document.getElementById('btnZoomOut').addEventListener('click', () => this.zoomOut());
        document.getElementById('btnFitView').addEventListener('click', () => this.fitView());
        document.getElementById('btnSaveWorkflow').addEventListener('click', () => this.saveWorkflowProperties());
        document.getElementById('btnSaveStep').addEventListener('click', () => this.saveStepProperties());

        this.canvas.addEventListener('contextmenu', (e) => e.preventDefault());

        this.canvas.addEventListener('click', (e) => {
            if (e.target === this.canvas || e.target.tagName === 'rect' && e.target.getAttribute('fill') === 'url(#grid)') {
                this.deselectAll();
            }
        });

        document.getElementById('ctxEditStep').addEventListener('click', () => this.editSelectedStep());
        document.getElementById('ctxDeleteStep').addEventListener('click', () => this.deleteSelectedStep());
        document.getElementById('ctxSetStartStep').addEventListener('click', () => this.setStartStep());
        document.getElementById('ctxDuplicateStep').addEventListener('click', () => this.duplicateSelectedStep());
    }

    setupDragAndDrop() {
        const container = document.getElementById('canvasContainer');

        container.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
        });

        container.addEventListener('drop', (e) => {
            e.preventDefault();
            if (this.draggedProcessor) {
                const rect = container.getBoundingClientRect();
                const x = (e.clientX - rect.left - this.panX) / this.zoom;
                const y = (e.clientY - rect.top - this.panY) / this.zoom;
                this.addStep(this.draggedProcessor, x, y);
            }
        });
    }

    newWorkflow() {
        const modal = new bootstrap.Modal(document.getElementById('workflowModal'));
        document.getElementById('workflowModalTitle').textContent = 'New Workflow';
        document.getElementById('workflowForm').reset();
        modal.show();
    }

    async saveWorkflowProperties() {
        const name = document.getElementById('wfName').value;
        const description = document.getElementById('wfDescription').value;
        const organization = document.getElementById('wfOrganization').value;
        const module = document.getElementById('wfModule').value;
        const category = document.getElementById('wfCategory').value;

        try {
            if (this.workflow) {
                this.workflow = await api.updateWorkflow(this.workflow.id, {
                    name, description, category
                });
            } else {
                this.workflow = await api.createWorkflow({
                    name, description, organization, module, category
                });
            }

            document.getElementById('workflowName').textContent = this.workflow.name;
            document.getElementById('workflowStatus').textContent = this.workflow.lifecycleState;

            bootstrap.Modal.getInstance(document.getElementById('workflowModal')).hide();
        } catch (error) {
            alert('Failed to save workflow: ' + error.message);
        }
    }

    addStep(processor, x, y) {
        const stepId = 'step-' + Date.now();
        const step = {
            id: stepId,
            name: processor.displayName,
            type: 'PROCESSOR',
            processor: {
                organization: processor.pluginOrganization,
                module: processor.pluginModule,
                version: processor.pluginVersion,
                processorName: processor.processorName
            },
            inputConfig: {},
            position: { x, y, width: 180, height: 60 }
        };

        this.steps.set(stepId, step);
        this.renderStep(step);

        if (this.steps.size === 1 && this.version) {
            this.version.startStepId = stepId;
        }
    }

    renderStep(step) {
        const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        g.setAttribute('class', 'workflow-step');
        g.setAttribute('data-step-id', step.id);
        g.setAttribute('transform', `translate(${step.position.x}, ${step.position.y})`);

        const isStart = this.version && this.version.startStepId === step.id;
        if (isStart) {
            g.classList.add('start-step');
        }

        g.innerHTML = `
            <rect width="${step.position.width}" height="${step.position.height}" rx="8"></rect>
            <text x="${step.position.width / 2}" y="25" text-anchor="middle" class="step-name" font-size="13">${escapeHtml(step.name)}</text>
            <text x="${step.position.width / 2}" y="42" text-anchor="middle" class="step-type" font-size="10" fill="#6c757d">
                ${step.processor ? step.processor.processorName : 'Workflow'}
            </text>
            ${isStart ? '<circle cx="10" cy="10" r="5" fill="#198754"/>' : ''}
        `;

        this.setupStepInteractions(g, step);
        this.stepsLayer.appendChild(g);
    }

    setupStepInteractions(g, step) {
        let isDragging = false;
        let startX, startY;

        g.addEventListener('mousedown', (e) => {
            if (e.button === 0) {
                isDragging = true;
                startX = e.clientX - step.position.x;
                startY = e.clientY - step.position.y;
                this.selectStep(step.id);
            }
        });

        document.addEventListener('mousemove', (e) => {
            if (isDragging) {
                step.position.x = (e.clientX - startX) / this.zoom;
                step.position.y = (e.clientY - startY) / this.zoom;
                g.setAttribute('transform', `translate(${step.position.x}, ${step.position.y})`);
                this.updateConnections(step.id);
            }
        });

        document.addEventListener('mouseup', () => {
            isDragging = false;
        });

        g.addEventListener('dblclick', () => {
            this.editStep(step.id);
        });

        g.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            this.selectStep(step.id);
            this.showContextMenu(e.clientX, e.clientY);
        });
    }

    selectStep(stepId) {
        this.deselectAll();
        this.selectedStep = stepId;
        const g = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);
        if (g) {
            g.classList.add('selected');
        }
        this.showStepProperties(stepId);
    }

    deselectAll() {
        this.selectedStep = null;
        this.selectedConnection = null;
        this.stepsLayer.querySelectorAll('.selected').forEach(el => el.classList.remove('selected'));
        this.connectionsLayer.querySelectorAll('.selected').forEach(el => el.classList.remove('selected'));
        document.getElementById('propertiesPanel').innerHTML = `
            <div class="text-center text-muted py-5">
                <i class="bi bi-cursor display-4"></i>
                <p class="mt-3">Select a step to view its properties</p>
            </div>
        `;
        this.hideContextMenu();
    }

    showStepProperties(stepId) {
        const step = this.steps.get(stepId);
        if (!step) return;

        const panel = document.getElementById('propertiesPanel');
        panel.innerHTML = `
            <div class="property-group">
                <div class="property-group-header">Step Properties</div>
                <div class="property-field">
                    <label>Name</label>
                    <input type="text" class="form-control form-control-sm" id="propStepName"
                           value="${escapeHtml(step.name)}">
                </div>
                <div class="property-field">
                    <label>Type</label>
                    <input type="text" class="form-control form-control-sm" readonly
                           value="${step.type}">
                </div>
                ${step.processor ? `
                <div class="property-field">
                    <label>Processor</label>
                    <input type="text" class="form-control form-control-sm" readonly
                           value="${step.processor.processorName}">
                </div>
                ` : ''}
            </div>
            <div class="property-group">
                <div class="property-group-header">Configuration</div>
                <button class="btn btn-outline-primary btn-sm w-100" onclick="designer.editStep('${stepId}')">
                    <i class="bi bi-pencil"></i> Edit Configuration
                </button>
            </div>
            <div class="property-group">
                <div class="property-group-header">Actions</div>
                <div class="d-grid gap-2">
                    <button class="btn btn-outline-success btn-sm" onclick="designer.setAsStartStep('${stepId}')">
                        <i class="bi bi-flag"></i> Set as Start
                    </button>
                    <button class="btn btn-outline-danger btn-sm" onclick="designer.deleteStep('${stepId}')">
                        <i class="bi bi-trash"></i> Delete
                    </button>
                </div>
            </div>
        `;

        document.getElementById('propStepName').addEventListener('change', (e) => {
            step.name = e.target.value;
            const g = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);
            g.querySelector('.step-name').textContent = step.name;
        });
    }

    editStep(stepId) {
        const step = this.steps.get(stepId);
        if (!step) return;

        const modal = document.getElementById('stepModal');
        const body = document.getElementById('stepModalBody');

        let html = `
            <form id="stepConfigForm">
                <div class="mb-3">
                    <label class="form-label">Step Name</label>
                    <input type="text" class="form-control" id="stepName" value="${escapeHtml(step.name)}">
                </div>
        `;

        const processor = this.processors.find(p =>
            p.processorName === step.processor?.processorName
        );

        if (processor && processor.inputFields) {
            processor.inputFields.forEach(field => {
                const value = step.inputConfig?.[field.name] || field.defaultValue || '';
                html += this.renderInputField(field, value);
            });
        }

        html += '</form>';
        body.innerHTML = html;

        this.currentEditingStep = stepId;
        new bootstrap.Modal(modal).show();
    }

    renderInputField(field, value) {
        let inputHtml = '';
        const required = field.required ? 'required' : '';

        switch (field.type) {
            case 'STRING':
                inputHtml = `<input type="text" class="form-control" name="${field.name}"
                             value="${escapeHtml(value)}" ${required}>`;
                break;
            case 'NUMBER':
                inputHtml = `<input type="number" class="form-control" name="${field.name}"
                             value="${value}" ${required}>`;
                break;
            case 'BOOLEAN':
                inputHtml = `<div class="form-check">
                    <input type="checkbox" class="form-check-input" name="${field.name}"
                           ${value === true || value === 'true' ? 'checked' : ''}>
                </div>`;
                break;
            case 'SELECT':
                inputHtml = `<select class="form-select" name="${field.name}" ${required}>`;
                (field.options || []).forEach(opt => {
                    inputHtml += `<option value="${opt.value}" ${value === opt.value ? 'selected' : ''}>
                                  ${opt.label}</option>`;
                });
                inputHtml += '</select>';
                break;
            case 'CODE':
            case 'TEXTAREA':
                inputHtml = `<textarea class="form-control ${field.type === 'CODE' ? 'code-editor' : ''}"
                             name="${field.name}" rows="5" ${required}>${escapeHtml(value)}</textarea>`;
                break;
            case 'JSON':
                inputHtml = `<textarea class="form-control code-editor" name="${field.name}"
                             rows="8" ${required}>${typeof value === 'object' ? JSON.stringify(value, null, 2) : escapeHtml(value)}</textarea>`;
                break;
            default:
                inputHtml = `<input type="text" class="form-control" name="${field.name}"
                             value="${escapeHtml(value)}" ${required}>`;
        }

        return `
            <div class="mb-3">
                <label class="form-label">${field.displayName}${field.required ? ' *' : ''}</label>
                ${field.description ? `<small class="text-muted d-block mb-1">${field.description}</small>` : ''}
                ${inputHtml}
            </div>
        `;
    }

    saveStepProperties() {
        const step = this.steps.get(this.currentEditingStep);
        if (!step) return;

        const form = document.getElementById('stepConfigForm');
        const formData = new FormData(form);

        step.name = formData.get('stepName') || step.name;

        const inputConfig = {};
        for (const [key, value] of formData.entries()) {
            if (key !== 'stepName') {
                inputConfig[key] = value;
            }
        }
        step.inputConfig = inputConfig;

        const g = this.stepsLayer.querySelector(`[data-step-id="${this.currentEditingStep}"]`);
        if (g) {
            g.querySelector('.step-name').textContent = step.name;
        }

        bootstrap.Modal.getInstance(document.getElementById('stepModal')).hide();
    }

    deleteStep(stepId) {
        if (!confirm('Are you sure you want to delete this step?')) return;

        this.steps.delete(stepId);
        const g = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);
        if (g) g.remove();

        this.connections.forEach((conn, connId) => {
            if (conn.sourceStepId === stepId || conn.targetStepId === stepId) {
                this.connections.delete(connId);
                const line = this.connectionsLayer.querySelector(`[data-connection-id="${connId}"]`);
                if (line) line.remove();
            }
        });

        this.deselectAll();
    }

    setAsStartStep(stepId) {
        if (!this.version) {
            this.version = { startStepId: stepId, steps: [], connections: {} };
        }
        this.version.startStepId = stepId;

        this.stepsLayer.querySelectorAll('.start-step').forEach(g => {
            g.classList.remove('start-step');
            const circle = g.querySelector('circle');
            if (circle) circle.remove();
        });

        const g = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);
        if (g) {
            g.classList.add('start-step');
            const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            circle.setAttribute('cx', '10');
            circle.setAttribute('cy', '10');
            circle.setAttribute('r', '5');
            circle.setAttribute('fill', '#198754');
            g.insertBefore(circle, g.firstChild.nextSibling);
        }
    }

    updateConnections(stepId) {
        this.connections.forEach((conn, connId) => {
            if (conn.sourceStepId === stepId || conn.targetStepId === stepId) {
                this.renderConnection(conn);
            }
        });
    }

    renderConnection(conn) {
        let line = this.connectionsLayer.querySelector(`[data-connection-id="${conn.id}"]`);
        if (!line) {
            line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            line.setAttribute('class', 'connection-line');
            line.setAttribute('data-connection-id', conn.id);
            this.connectionsLayer.appendChild(line);
        }

        const source = this.steps.get(conn.sourceStepId);
        const target = this.steps.get(conn.targetStepId);

        if (source && target) {
            const x1 = source.position.x + source.position.width;
            const y1 = source.position.y + source.position.height / 2;
            const x2 = target.position.x;
            const y2 = target.position.y + target.position.height / 2;

            const dx = Math.abs(x2 - x1) / 2;
            const path = `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`;
            line.setAttribute('d', path);
        }
    }

    async saveWorkflow() {
        if (!this.workflow) {
            this.newWorkflow();
            return;
        }

        try {
            const versionData = {
                versionNumber: this.version?.versionNumber || '1.0.0',
                description: 'Updated from designer',
                startStepId: this.version?.startStepId,
                steps: Array.from(this.steps.values()),
                connections: Object.fromEntries(this.connections),
                contextRetention: 'FULL'
            };

            await api.createVersion(this.workflow.id, versionData);
            alert('Workflow saved successfully!');
        } catch (error) {
            alert('Failed to save workflow: ' + error.message);
        }
    }

    async validateWorkflow() {
        const versionData = {
            startStepId: this.version?.startStepId,
            steps: Array.from(this.steps.values()),
            connections: Object.fromEntries(this.connections)
        };

        try {
            const result = await api.validateWorkflow(versionData);
            this.showValidationResults(result);
        } catch (error) {
            alert('Validation failed: ' + error.message);
        }
    }

    showValidationResults(result) {
        const modal = document.getElementById('validationModal');
        const body = document.getElementById('validationResults');

        if (result.valid) {
            body.innerHTML = `
                <div class="validation-success">
                    <i class="bi bi-check-circle-fill text-success display-4"></i>
                    <h5 class="mt-3">Workflow is valid!</h5>
                    <p class="text-muted">No errors found.</p>
                </div>
            `;
        } else {
            let html = '';
            if (result.errors && result.errors.length > 0) {
                html += '<h6 class="text-danger"><i class="bi bi-x-circle"></i> Errors</h6>';
                result.errors.forEach(err => {
                    html += `<div class="validation-error">
                        <strong>${err.code}</strong>: ${err.message}
                        ${err.stepName ? `<br><small>Step: ${err.stepName}</small>` : ''}
                    </div>`;
                });
            }
            if (result.warnings && result.warnings.length > 0) {
                html += '<h6 class="text-warning mt-3"><i class="bi bi-exclamation-triangle"></i> Warnings</h6>';
                result.warnings.forEach(warn => {
                    html += `<div class="validation-warning">
                        <strong>${warn.code}</strong>: ${warn.message}
                        ${warn.stepName ? `<br><small>Step: ${warn.stepName}</small>` : ''}
                    </div>`;
                });
            }
            body.innerHTML = html;
        }

        new bootstrap.Modal(modal).show();
    }

    async executeWorkflow() {
        if (!this.workflow || !this.workflow.id) {
            alert('Please save the workflow first.');
            return;
        }

        if (!confirm('Execute this workflow?')) return;

        try {
            const execution = await api.startExecution(this.workflow.id, {});
            alert(`Workflow execution started! ID: ${execution.id}`);
            window.location.href = `/executions?id=${execution.id}`;
        } catch (error) {
            alert('Failed to execute workflow: ' + error.message);
        }
    }

    showContextMenu(x, y) {
        const menu = document.getElementById('contextMenu');
        menu.style.display = 'block';
        menu.style.left = x + 'px';
        menu.style.top = y + 'px';
    }

    hideContextMenu() {
        document.getElementById('contextMenu').style.display = 'none';
    }

    editSelectedStep() {
        if (this.selectedStep) {
            this.editStep(this.selectedStep);
        }
        this.hideContextMenu();
    }

    deleteSelectedStep() {
        if (this.selectedStep) {
            this.deleteStep(this.selectedStep);
        }
        this.hideContextMenu();
    }

    setStartStep() {
        if (this.selectedStep) {
            this.setAsStartStep(this.selectedStep);
        }
        this.hideContextMenu();
    }

    duplicateSelectedStep() {
        if (this.selectedStep) {
            const original = this.steps.get(this.selectedStep);
            if (original) {
                const processor = this.processors.find(p =>
                    p.processorName === original.processor?.processorName
                );
                if (processor) {
                    this.addStep(processor, original.position.x + 50, original.position.y + 50);
                }
            }
        }
        this.hideContextMenu();
    }

    zoomIn() {
        this.zoom = Math.min(this.zoom * 1.2, 3);
        this.applyZoom();
    }

    zoomOut() {
        this.zoom = Math.max(this.zoom / 1.2, 0.3);
        this.applyZoom();
    }

    fitView() {
        this.zoom = 1;
        this.panX = 0;
        this.panY = 0;
        this.applyZoom();
    }

    applyZoom() {
        const g = this.canvas.querySelector('g:last-child');
        if (g) {
            this.stepsLayer.setAttribute('transform', `scale(${this.zoom}) translate(${this.panX}, ${this.panY})`);
            this.connectionsLayer.setAttribute('transform', `scale(${this.zoom}) translate(${this.panX}, ${this.panY})`);
        }
    }
}

let designer;
document.addEventListener('DOMContentLoaded', () => {
    designer = new WorkflowDesigner();
});
