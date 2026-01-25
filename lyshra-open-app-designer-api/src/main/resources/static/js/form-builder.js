/**
 * Form Builder for Processor Configuration
 * Provides dynamic form generation with real-time validation for workflow steps.
 */

class FormBuilder {
    constructor(options = {}) {
        this.container = null;
        this.fields = [];
        this.values = {};
        this.errors = new Map();
        this.touched = new Set();
        this.onChange = options.onChange || (() => {});
        this.onValidate = options.onValidate || (() => {});
        this.debounceTimeout = null;
        this.debounceDelay = 300;
    }

    /**
     * Build a form from processor metadata
     * @param {HTMLElement} container - Container element
     * @param {Object} processor - Processor metadata with inputFields
     * @param {Object} initialValues - Initial field values
     */
    build(container, processor, initialValues = {}) {
        this.container = container;
        this.fields = processor.inputFields || [];
        this.values = { ...initialValues };
        this.errors.clear();
        this.touched.clear();

        this.render();
    }

    /**
     * Render the entire form
     */
    render() {
        if (!this.container) return;

        // Group fields by category
        const groups = this.groupFieldsByCategory(this.fields);

        let html = '<div class="processor-config-form">';

        for (const [category, fields] of Object.entries(groups)) {
            html += this.renderFieldGroup(category, fields);
        }

        html += '</div>';
        this.container.innerHTML = html;

        // Attach event listeners
        this.attachEventListeners();

        // Run initial validation
        this.validateAll(false);
    }

    /**
     * Group fields by category
     */
    groupFieldsByCategory(fields) {
        const groups = {};
        fields.forEach(field => {
            const category = field.category || 'Configuration';
            if (!groups[category]) {
                groups[category] = [];
            }
            groups[category].push(field);
        });
        return groups;
    }

    /**
     * Render a group of fields
     */
    renderFieldGroup(category, fields) {
        const categoryId = category.replace(/\s+/g, '-').toLowerCase();
        return `
            <div class="field-group mb-4" data-category="${categoryId}">
                <div class="field-group-header">
                    <i class="bi bi-gear"></i> ${escapeHtml(category)}
                </div>
                <div class="field-group-body">
                    ${fields.map(field => this.renderField(field)).join('')}
                </div>
            </div>
        `;
    }

    /**
     * Render a single field based on its type
     */
    renderField(field) {
        const value = this.values[field.name] ?? field.defaultValue ?? '';
        const error = this.errors.get(field.name);
        const touched = this.touched.has(field.name);
        const hasError = touched && error;
        const isRequired = field.required;

        const fieldHtml = this.renderFieldInput(field, value);

        return `
            <div class="form-field mb-3 ${hasError ? 'has-error' : ''}" data-field="${field.name}">
                <label class="form-label">
                    ${escapeHtml(field.displayName || field.name)}
                    ${isRequired ? '<span class="text-danger">*</span>' : ''}
                    ${field.tooltip ? `
                        <i class="bi bi-question-circle text-muted ms-1"
                           data-bs-toggle="tooltip"
                           title="${escapeHtml(field.tooltip)}"></i>
                    ` : ''}
                </label>
                ${field.description ? `<small class="form-text text-muted d-block mb-1">${escapeHtml(field.description)}</small>` : ''}
                ${fieldHtml}
                <div class="invalid-feedback" id="error-${field.name}">${hasError ? escapeHtml(error) : ''}</div>
                ${field.hint ? `<small class="form-text text-muted">${escapeHtml(field.hint)}</small>` : ''}
            </div>
        `;
    }

    /**
     * Render the input element based on field type
     */
    renderFieldInput(field, value) {
        const inputId = `field-${field.name}`;
        const inputName = field.name;
        const required = field.required ? 'required' : '';
        const disabled = field.disabled ? 'disabled' : '';
        const readonly = field.readonly ? 'readonly' : '';
        const placeholder = field.placeholder || '';

        switch (field.type) {
            case 'STRING':
                return this.renderStringField(field, value, inputId, inputName, required, disabled, readonly, placeholder);

            case 'TEXT':
            case 'TEXTAREA':
                return this.renderTextareaField(field, value, inputId, inputName, required, disabled, readonly, placeholder);

            case 'NUMBER':
                return this.renderNumberField(field, value, inputId, inputName, required, disabled, readonly, placeholder);

            case 'BOOLEAN':
                return this.renderBooleanField(field, value, inputId, inputName, disabled);

            case 'SELECT':
                return this.renderSelectField(field, value, inputId, inputName, required, disabled);

            case 'MULTI_SELECT':
                return this.renderMultiSelectField(field, value, inputId, inputName, required, disabled);

            case 'DATE':
                return this.renderDateField(field, value, inputId, inputName, required, disabled, readonly);

            case 'DATETIME':
                return this.renderDateTimeField(field, value, inputId, inputName, required, disabled, readonly);

            case 'TIME':
                return this.renderTimeField(field, value, inputId, inputName, required, disabled, readonly);

            case 'JSON':
                return this.renderJsonField(field, value, inputId, inputName, required, disabled, readonly);

            case 'CODE':
                return this.renderCodeField(field, value, inputId, inputName, required, disabled, readonly, placeholder);

            case 'EXPRESSION':
                return this.renderExpressionField(field, value, inputId, inputName, required, disabled, readonly, placeholder);

            case 'PASSWORD':
                return this.renderPasswordField(field, value, inputId, inputName, required, disabled, readonly, placeholder);

            case 'URL':
                return this.renderUrlField(field, value, inputId, inputName, required, disabled, readonly, placeholder);

            case 'EMAIL':
                return this.renderEmailField(field, value, inputId, inputName, required, disabled, readonly, placeholder);

            case 'COLOR':
                return this.renderColorField(field, value, inputId, inputName, required, disabled);

            case 'VARIABLE_REFERENCE':
                return this.renderVariableRefField(field, value, inputId, inputName, required, disabled);

            case 'STEP_REFERENCE':
                return this.renderStepRefField(field, value, inputId, inputName, required, disabled);

            case 'KEY_VALUE_PAIRS':
                return this.renderKeyValueField(field, value, inputId, inputName, required, disabled);

            case 'ARRAY':
                return this.renderArrayField(field, value, inputId, inputName, required, disabled);

            case 'DURATION':
                return this.renderDurationField(field, value, inputId, inputName, required, disabled, readonly);

            case 'FILE':
                return this.renderFileField(field, value, inputId, inputName, required, disabled);

            default:
                return this.renderStringField(field, value, inputId, inputName, required, disabled, readonly, placeholder);
        }
    }

    // ============================================
    // Field Type Renderers
    // ============================================

    renderStringField(field, value, inputId, inputName, required, disabled, readonly, placeholder) {
        const maxLength = field.maxLength ? `maxlength="${field.maxLength}"` : '';
        const minLength = field.minLength ? `minlength="${field.minLength}"` : '';
        const pattern = field.pattern ? `pattern="${escapeHtml(field.pattern)}"` : '';

        return `
            <input type="text"
                   class="form-control"
                   id="${inputId}"
                   name="${inputName}"
                   value="${escapeHtml(value)}"
                   placeholder="${escapeHtml(placeholder)}"
                   ${required} ${disabled} ${readonly} ${maxLength} ${minLength} ${pattern}>
        `;
    }

    renderTextareaField(field, value, inputId, inputName, required, disabled, readonly, placeholder) {
        const rows = field.rows || 4;
        const maxLength = field.maxLength ? `maxlength="${field.maxLength}"` : '';

        return `
            <textarea class="form-control"
                      id="${inputId}"
                      name="${inputName}"
                      rows="${rows}"
                      placeholder="${escapeHtml(placeholder)}"
                      ${required} ${disabled} ${readonly} ${maxLength}>${escapeHtml(value)}</textarea>
        `;
    }

    renderNumberField(field, value, inputId, inputName, required, disabled, readonly, placeholder) {
        const min = field.min !== undefined ? `min="${field.min}"` : '';
        const max = field.max !== undefined ? `max="${field.max}"` : '';
        const step = field.step !== undefined ? `step="${field.step}"` : '';

        return `
            <input type="number"
                   class="form-control"
                   id="${inputId}"
                   name="${inputName}"
                   value="${value}"
                   placeholder="${escapeHtml(placeholder)}"
                   ${required} ${disabled} ${readonly} ${min} ${max} ${step}>
        `;
    }

    renderBooleanField(field, value, inputId, inputName, disabled) {
        const checked = value === true || value === 'true' ? 'checked' : '';

        return `
            <div class="form-check form-switch">
                <input type="checkbox"
                       class="form-check-input"
                       id="${inputId}"
                       name="${inputName}"
                       ${checked} ${disabled}>
                <label class="form-check-label" for="${inputId}">
                    ${field.checkboxLabel || 'Enable'}
                </label>
            </div>
        `;
    }

    renderSelectField(field, value, inputId, inputName, required, disabled) {
        const options = field.options || [];
        const optionsHtml = options.map(opt => {
            const optValue = typeof opt === 'object' ? opt.value : opt;
            const optLabel = typeof opt === 'object' ? opt.label : opt;
            const selected = optValue === value ? 'selected' : '';
            return `<option value="${escapeHtml(optValue)}" ${selected}>${escapeHtml(optLabel)}</option>`;
        }).join('');

        return `
            <select class="form-select"
                    id="${inputId}"
                    name="${inputName}"
                    ${required} ${disabled}>
                <option value="">-- Select --</option>
                ${optionsHtml}
            </select>
        `;
    }

    renderMultiSelectField(field, value, inputId, inputName, required, disabled) {
        const options = field.options || [];
        const selectedValues = Array.isArray(value) ? value : (value ? [value] : []);

        const checkboxes = options.map((opt, idx) => {
            const optValue = typeof opt === 'object' ? opt.value : opt;
            const optLabel = typeof opt === 'object' ? opt.label : opt;
            const checked = selectedValues.includes(optValue) ? 'checked' : '';
            return `
                <div class="form-check">
                    <input type="checkbox"
                           class="form-check-input multi-select-checkbox"
                           id="${inputId}-${idx}"
                           name="${inputName}"
                           value="${escapeHtml(optValue)}"
                           ${checked} ${disabled}>
                    <label class="form-check-label" for="${inputId}-${idx}">${escapeHtml(optLabel)}</label>
                </div>
            `;
        }).join('');

        return `<div class="multi-select-container border rounded p-2">${checkboxes}</div>`;
    }

    renderDateField(field, value, inputId, inputName, required, disabled, readonly) {
        const min = field.minDate ? `min="${field.minDate}"` : '';
        const max = field.maxDate ? `max="${field.maxDate}"` : '';

        return `
            <input type="date"
                   class="form-control"
                   id="${inputId}"
                   name="${inputName}"
                   value="${escapeHtml(value)}"
                   ${required} ${disabled} ${readonly} ${min} ${max}>
        `;
    }

    renderDateTimeField(field, value, inputId, inputName, required, disabled, readonly) {
        return `
            <input type="datetime-local"
                   class="form-control"
                   id="${inputId}"
                   name="${inputName}"
                   value="${escapeHtml(value)}"
                   ${required} ${disabled} ${readonly}>
        `;
    }

    renderTimeField(field, value, inputId, inputName, required, disabled, readonly) {
        return `
            <input type="time"
                   class="form-control"
                   id="${inputId}"
                   name="${inputName}"
                   value="${escapeHtml(value)}"
                   ${required} ${disabled} ${readonly}>
        `;
    }

    renderJsonField(field, value, inputId, inputName, required, disabled, readonly) {
        const rows = field.rows || 8;
        const displayValue = typeof value === 'object' ? JSON.stringify(value, null, 2) : value;

        return `
            <div class="json-editor-wrapper">
                <div class="json-toolbar mb-1">
                    <button type="button" class="btn btn-sm btn-outline-secondary" onclick="formBuilder.formatJson('${inputName}')">
                        <i class="bi bi-code-square"></i> Format
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-secondary" onclick="formBuilder.validateJson('${inputName}')">
                        <i class="bi bi-check-circle"></i> Validate JSON
                    </button>
                </div>
                <textarea class="form-control code-editor json-editor"
                          id="${inputId}"
                          name="${inputName}"
                          rows="${rows}"
                          spellcheck="false"
                          ${required} ${disabled} ${readonly}>${escapeHtml(displayValue)}</textarea>
                <div class="json-validation-status mt-1" id="json-status-${inputName}"></div>
            </div>
        `;
    }

    renderCodeField(field, value, inputId, inputName, required, disabled, readonly, placeholder) {
        const rows = field.rows || 10;
        const language = field.language || 'javascript';

        return `
            <div class="code-editor-wrapper">
                <div class="code-toolbar mb-1 d-flex justify-content-between align-items-center">
                    <span class="badge bg-secondary">${escapeHtml(language)}</span>
                    <div>
                        <button type="button" class="btn btn-sm btn-outline-secondary" onclick="formBuilder.formatCode('${inputName}', '${language}')">
                            <i class="bi bi-code-square"></i> Format
                        </button>
                    </div>
                </div>
                <textarea class="form-control code-editor"
                          id="${inputId}"
                          name="${inputName}"
                          rows="${rows}"
                          spellcheck="false"
                          placeholder="${escapeHtml(placeholder)}"
                          data-language="${language}"
                          ${required} ${disabled} ${readonly}>${escapeHtml(value)}</textarea>
            </div>
        `;
    }

    renderExpressionField(field, value, inputId, inputName, required, disabled, readonly, placeholder) {
        return `
            <div class="expression-editor-wrapper">
                <div class="input-group">
                    <span class="input-group-text"><i class="bi bi-braces"></i></span>
                    <input type="text"
                           class="form-control font-monospace"
                           id="${inputId}"
                           name="${inputName}"
                           value="${escapeHtml(value)}"
                           placeholder="${escapeHtml(placeholder || 'e.g., ${variables.myVar} or #{expression}')}"
                           ${required} ${disabled} ${readonly}>
                    <button type="button" class="btn btn-outline-secondary" onclick="formBuilder.showExpressionHelper('${inputName}')" title="Expression Builder">
                        <i class="bi bi-magic"></i>
                    </button>
                </div>
                <small class="form-text text-muted">
                    Use <code>\${var}</code> for variables or <code>#{expr}</code> for expressions
                </small>
            </div>
        `;
    }

    renderPasswordField(field, value, inputId, inputName, required, disabled, readonly, placeholder) {
        return `
            <div class="input-group">
                <input type="password"
                       class="form-control"
                       id="${inputId}"
                       name="${inputName}"
                       value="${escapeHtml(value)}"
                       placeholder="${escapeHtml(placeholder)}"
                       ${required} ${disabled} ${readonly}>
                <button type="button" class="btn btn-outline-secondary" onclick="formBuilder.togglePassword('${inputId}')">
                    <i class="bi bi-eye"></i>
                </button>
            </div>
        `;
    }

    renderUrlField(field, value, inputId, inputName, required, disabled, readonly, placeholder) {
        return `
            <div class="input-group">
                <span class="input-group-text"><i class="bi bi-link-45deg"></i></span>
                <input type="url"
                       class="form-control"
                       id="${inputId}"
                       name="${inputName}"
                       value="${escapeHtml(value)}"
                       placeholder="${escapeHtml(placeholder || 'https://example.com')}"
                       ${required} ${disabled} ${readonly}>
            </div>
        `;
    }

    renderEmailField(field, value, inputId, inputName, required, disabled, readonly, placeholder) {
        return `
            <div class="input-group">
                <span class="input-group-text"><i class="bi bi-envelope"></i></span>
                <input type="email"
                       class="form-control"
                       id="${inputId}"
                       name="${inputName}"
                       value="${escapeHtml(value)}"
                       placeholder="${escapeHtml(placeholder || 'user@example.com')}"
                       ${required} ${disabled} ${readonly}>
            </div>
        `;
    }

    renderColorField(field, value, inputId, inputName, required, disabled) {
        const colorValue = value || '#0d6efd';

        return `
            <div class="input-group">
                <input type="color"
                       class="form-control form-control-color"
                       id="${inputId}"
                       name="${inputName}"
                       value="${escapeHtml(colorValue)}"
                       ${required} ${disabled}>
                <input type="text"
                       class="form-control"
                       value="${escapeHtml(colorValue)}"
                       id="${inputId}-text"
                       onchange="document.getElementById('${inputId}').value = this.value"
                       ${disabled}>
            </div>
        `;
    }

    renderVariableRefField(field, value, inputId, inputName, required, disabled) {
        // This would be populated dynamically with available workflow variables
        return `
            <div class="variable-ref-wrapper">
                <select class="form-select"
                        id="${inputId}"
                        name="${inputName}"
                        ${required} ${disabled}>
                    <option value="">-- Select Variable --</option>
                    <option value="$input" ${value === '$input' ? 'selected' : ''}>Input Data ($input)</option>
                    <option value="$context" ${value === '$context' ? 'selected' : ''}>Context ($context)</option>
                    <option value="$env" ${value === '$env' ? 'selected' : ''}>Environment ($env)</option>
                </select>
                <small class="form-text text-muted">Or enter custom: <code>\${stepName.output.field}</code></small>
            </div>
        `;
    }

    renderStepRefField(field, value, inputId, inputName, required, disabled) {
        // This would be populated dynamically with available workflow steps
        return `
            <select class="form-select step-ref-select"
                    id="${inputId}"
                    name="${inputName}"
                    ${required} ${disabled}>
                <option value="">-- Select Step --</option>
            </select>
        `;
    }

    renderKeyValueField(field, value, inputId, inputName, required, disabled) {
        const pairs = Array.isArray(value) ? value : (value ? Object.entries(value).map(([k, v]) => ({ key: k, value: v })) : []);

        const rowsHtml = pairs.map((pair, idx) => `
            <div class="kv-row d-flex gap-2 mb-2" data-index="${idx}">
                <input type="text" class="form-control kv-key" placeholder="Key" value="${escapeHtml(pair.key || '')}" ${disabled}>
                <input type="text" class="form-control kv-value" placeholder="Value" value="${escapeHtml(pair.value || '')}" ${disabled}>
                <button type="button" class="btn btn-outline-danger btn-sm" onclick="formBuilder.removeKeyValueRow('${inputName}', ${idx})" ${disabled}>
                    <i class="bi bi-trash"></i>
                </button>
            </div>
        `).join('');

        return `
            <div class="key-value-editor" id="${inputId}" data-field="${inputName}">
                <div class="kv-rows">
                    ${rowsHtml}
                </div>
                <button type="button" class="btn btn-outline-secondary btn-sm" onclick="formBuilder.addKeyValueRow('${inputName}')" ${disabled}>
                    <i class="bi bi-plus"></i> Add Entry
                </button>
            </div>
        `;
    }

    renderArrayField(field, value, inputId, inputName, required, disabled) {
        const items = Array.isArray(value) ? value : (value ? [value] : []);
        const itemType = field.itemType || 'STRING';

        const rowsHtml = items.map((item, idx) => `
            <div class="array-item d-flex gap-2 mb-2" data-index="${idx}">
                <input type="text" class="form-control array-item-input" value="${escapeHtml(item)}" ${disabled}>
                <button type="button" class="btn btn-outline-danger btn-sm" onclick="formBuilder.removeArrayItem('${inputName}', ${idx})" ${disabled}>
                    <i class="bi bi-trash"></i>
                </button>
            </div>
        `).join('');

        return `
            <div class="array-editor" id="${inputId}" data-field="${inputName}" data-item-type="${itemType}">
                <div class="array-items">
                    ${rowsHtml}
                </div>
                <button type="button" class="btn btn-outline-secondary btn-sm" onclick="formBuilder.addArrayItem('${inputName}')" ${disabled}>
                    <i class="bi bi-plus"></i> Add Item
                </button>
            </div>
        `;
    }

    renderDurationField(field, value, inputId, inputName, required, disabled, readonly) {
        // Parse duration value (e.g., "PT1H30M" or "5000" milliseconds)
        let hours = 0, minutes = 0, seconds = 0;
        if (value) {
            if (typeof value === 'string' && value.startsWith('PT')) {
                const match = value.match(/PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?/);
                if (match) {
                    hours = parseInt(match[1]) || 0;
                    minutes = parseInt(match[2]) || 0;
                    seconds = parseInt(match[3]) || 0;
                }
            } else if (typeof value === 'number') {
                seconds = Math.floor(value / 1000);
                minutes = Math.floor(seconds / 60);
                hours = Math.floor(minutes / 60);
                seconds = seconds % 60;
                minutes = minutes % 60;
            }
        }

        return `
            <div class="duration-editor d-flex gap-2" id="${inputId}" data-field="${inputName}">
                <div class="input-group">
                    <input type="number" class="form-control duration-hours" value="${hours}" min="0" placeholder="0" ${disabled} ${readonly}>
                    <span class="input-group-text">h</span>
                </div>
                <div class="input-group">
                    <input type="number" class="form-control duration-minutes" value="${minutes}" min="0" max="59" placeholder="0" ${disabled} ${readonly}>
                    <span class="input-group-text">m</span>
                </div>
                <div class="input-group">
                    <input type="number" class="form-control duration-seconds" value="${seconds}" min="0" max="59" placeholder="0" ${disabled} ${readonly}>
                    <span class="input-group-text">s</span>
                </div>
            </div>
        `;
    }

    renderFileField(field, value, inputId, inputName, required, disabled) {
        const accept = field.accept || '*/*';
        const multiple = field.multiple ? 'multiple' : '';

        return `
            <input type="file"
                   class="form-control"
                   id="${inputId}"
                   name="${inputName}"
                   accept="${escapeHtml(accept)}"
                   ${required} ${disabled} ${multiple}>
        `;
    }

    // ============================================
    // Event Handling
    // ============================================

    attachEventListeners() {
        if (!this.container) return;

        // Input change events
        this.container.querySelectorAll('input, select, textarea').forEach(input => {
            const fieldName = input.name;
            if (!fieldName) return;

            // Mark as touched on focus
            input.addEventListener('focus', () => {
                this.touched.add(fieldName);
            });

            // Handle changes
            input.addEventListener('input', (e) => this.handleInputChange(e, fieldName));
            input.addEventListener('change', (e) => this.handleInputChange(e, fieldName));
        });

        // Multi-select checkboxes
        this.container.querySelectorAll('.multi-select-checkbox').forEach(checkbox => {
            checkbox.addEventListener('change', () => {
                const fieldName = checkbox.name;
                this.handleMultiSelectChange(fieldName);
            });
        });

        // Key-value pair changes
        this.container.querySelectorAll('.kv-key, .kv-value').forEach(input => {
            input.addEventListener('input', () => {
                const editor = input.closest('.key-value-editor');
                if (editor) {
                    this.handleKeyValueChange(editor.dataset.field);
                }
            });
        });

        // Array item changes
        this.container.querySelectorAll('.array-item-input').forEach(input => {
            input.addEventListener('input', () => {
                const editor = input.closest('.array-editor');
                if (editor) {
                    this.handleArrayChange(editor.dataset.field);
                }
            });
        });

        // Duration changes
        this.container.querySelectorAll('.duration-hours, .duration-minutes, .duration-seconds').forEach(input => {
            input.addEventListener('input', () => {
                const editor = input.closest('.duration-editor');
                if (editor) {
                    this.handleDurationChange(editor.dataset.field);
                }
            });
        });

        // Color text input sync
        this.container.querySelectorAll('input[type="color"]').forEach(colorInput => {
            colorInput.addEventListener('input', () => {
                const textInput = document.getElementById(colorInput.id + '-text');
                if (textInput) {
                    textInput.value = colorInput.value;
                }
            });
        });

        // Initialize tooltips
        if (typeof bootstrap !== 'undefined' && bootstrap.Tooltip) {
            this.container.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(el => {
                new bootstrap.Tooltip(el);
            });
        }
    }

    handleInputChange(event, fieldName) {
        const input = event.target;
        let value;

        if (input.type === 'checkbox') {
            value = input.checked;
        } else if (input.type === 'number') {
            value = input.value === '' ? null : parseFloat(input.value);
        } else {
            value = input.value;
        }

        this.values[fieldName] = value;
        this.touched.add(fieldName);

        // Debounced validation
        this.debouncedValidate(fieldName);

        // Notify change
        this.onChange(fieldName, value, this.values);
    }

    handleMultiSelectChange(fieldName) {
        const checkboxes = this.container.querySelectorAll(`input[name="${fieldName}"].multi-select-checkbox:checked`);
        const values = Array.from(checkboxes).map(cb => cb.value);
        this.values[fieldName] = values;
        this.touched.add(fieldName);
        this.debouncedValidate(fieldName);
        this.onChange(fieldName, values, this.values);
    }

    handleKeyValueChange(fieldName) {
        const editor = this.container.querySelector(`.key-value-editor[data-field="${fieldName}"]`);
        if (!editor) return;

        const pairs = {};
        editor.querySelectorAll('.kv-row').forEach(row => {
            const key = row.querySelector('.kv-key').value.trim();
            const value = row.querySelector('.kv-value').value;
            if (key) {
                pairs[key] = value;
            }
        });

        this.values[fieldName] = pairs;
        this.touched.add(fieldName);
        this.debouncedValidate(fieldName);
        this.onChange(fieldName, pairs, this.values);
    }

    handleArrayChange(fieldName) {
        const editor = this.container.querySelector(`.array-editor[data-field="${fieldName}"]`);
        if (!editor) return;

        const items = [];
        editor.querySelectorAll('.array-item-input').forEach(input => {
            if (input.value.trim()) {
                items.push(input.value);
            }
        });

        this.values[fieldName] = items;
        this.touched.add(fieldName);
        this.debouncedValidate(fieldName);
        this.onChange(fieldName, items, this.values);
    }

    handleDurationChange(fieldName) {
        const editor = this.container.querySelector(`.duration-editor[data-field="${fieldName}"]`);
        if (!editor) return;

        const hours = parseInt(editor.querySelector('.duration-hours').value) || 0;
        const minutes = parseInt(editor.querySelector('.duration-minutes').value) || 0;
        const seconds = parseInt(editor.querySelector('.duration-seconds').value) || 0;

        // Convert to ISO 8601 duration format
        let duration = 'PT';
        if (hours > 0) duration += `${hours}H`;
        if (minutes > 0) duration += `${minutes}M`;
        if (seconds > 0 || duration === 'PT') duration += `${seconds}S`;

        this.values[fieldName] = duration;
        this.touched.add(fieldName);
        this.debouncedValidate(fieldName);
        this.onChange(fieldName, duration, this.values);
    }

    // ============================================
    // Dynamic Field Operations
    // ============================================

    addKeyValueRow(fieldName) {
        const editor = this.container.querySelector(`.key-value-editor[data-field="${fieldName}"]`);
        if (!editor) return;

        const rowsContainer = editor.querySelector('.kv-rows');
        const index = rowsContainer.children.length;

        const row = document.createElement('div');
        row.className = 'kv-row d-flex gap-2 mb-2';
        row.dataset.index = index;
        row.innerHTML = `
            <input type="text" class="form-control kv-key" placeholder="Key" value="">
            <input type="text" class="form-control kv-value" placeholder="Value" value="">
            <button type="button" class="btn btn-outline-danger btn-sm" onclick="formBuilder.removeKeyValueRow('${fieldName}', ${index})">
                <i class="bi bi-trash"></i>
            </button>
        `;

        rowsContainer.appendChild(row);

        // Add event listeners to new inputs
        row.querySelectorAll('.kv-key, .kv-value').forEach(input => {
            input.addEventListener('input', () => this.handleKeyValueChange(fieldName));
        });
    }

    removeKeyValueRow(fieldName, index) {
        const editor = this.container.querySelector(`.key-value-editor[data-field="${fieldName}"]`);
        if (!editor) return;

        const row = editor.querySelector(`.kv-row[data-index="${index}"]`);
        if (row) {
            row.remove();
            this.handleKeyValueChange(fieldName);
        }
    }

    addArrayItem(fieldName) {
        const editor = this.container.querySelector(`.array-editor[data-field="${fieldName}"]`);
        if (!editor) return;

        const itemsContainer = editor.querySelector('.array-items');
        const index = itemsContainer.children.length;

        const item = document.createElement('div');
        item.className = 'array-item d-flex gap-2 mb-2';
        item.dataset.index = index;
        item.innerHTML = `
            <input type="text" class="form-control array-item-input" value="">
            <button type="button" class="btn btn-outline-danger btn-sm" onclick="formBuilder.removeArrayItem('${fieldName}', ${index})">
                <i class="bi bi-trash"></i>
            </button>
        `;

        itemsContainer.appendChild(item);

        // Add event listener
        item.querySelector('.array-item-input').addEventListener('input', () => this.handleArrayChange(fieldName));
    }

    removeArrayItem(fieldName, index) {
        const editor = this.container.querySelector(`.array-editor[data-field="${fieldName}"]`);
        if (!editor) return;

        const item = editor.querySelector(`.array-item[data-index="${index}"]`);
        if (item) {
            item.remove();
            this.handleArrayChange(fieldName);
        }
    }

    // ============================================
    // Validation
    // ============================================

    debouncedValidate(fieldName) {
        if (this.debounceTimeout) {
            clearTimeout(this.debounceTimeout);
        }

        this.debounceTimeout = setTimeout(() => {
            this.validateField(fieldName);
        }, this.debounceDelay);
    }

    validateField(fieldName) {
        const field = this.fields.find(f => f.name === fieldName);
        if (!field) return true;

        const value = this.values[fieldName];
        const errors = [];

        // Required validation
        if (field.required && this.isEmpty(value)) {
            errors.push(`${field.displayName || field.name} is required`);
        }

        // Type-specific validation
        if (!this.isEmpty(value)) {
            switch (field.type) {
                case 'NUMBER':
                    if (typeof value !== 'number' || isNaN(value)) {
                        errors.push('Must be a valid number');
                    } else {
                        if (field.min !== undefined && value < field.min) {
                            errors.push(`Minimum value is ${field.min}`);
                        }
                        if (field.max !== undefined && value > field.max) {
                            errors.push(`Maximum value is ${field.max}`);
                        }
                    }
                    break;

                case 'STRING':
                case 'TEXT':
                    if (field.minLength && value.length < field.minLength) {
                        errors.push(`Minimum length is ${field.minLength} characters`);
                    }
                    if (field.maxLength && value.length > field.maxLength) {
                        errors.push(`Maximum length is ${field.maxLength} characters`);
                    }
                    if (field.pattern) {
                        const regex = new RegExp(field.pattern);
                        if (!regex.test(value)) {
                            errors.push(field.patternMessage || 'Invalid format');
                        }
                    }
                    break;

                case 'EMAIL':
                    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
                        errors.push('Invalid email address');
                    }
                    break;

                case 'URL':
                    try {
                        new URL(value);
                    } catch {
                        errors.push('Invalid URL');
                    }
                    break;

                case 'JSON':
                    try {
                        if (typeof value === 'string') {
                            JSON.parse(value);
                        }
                    } catch (e) {
                        errors.push('Invalid JSON: ' + e.message);
                    }
                    break;

                case 'EXPRESSION':
                    const exprError = this.validateExpression(value);
                    if (exprError) {
                        errors.push(exprError);
                    }
                    break;
            }

            // Custom validators
            if (field.validators) {
                for (const validator of field.validators) {
                    const error = this.runCustomValidator(validator, value, field);
                    if (error) {
                        errors.push(error);
                    }
                }
            }
        }

        // Update error state
        if (errors.length > 0) {
            this.errors.set(fieldName, errors[0]); // Show first error
        } else {
            this.errors.delete(fieldName);
        }

        // Update UI
        this.updateFieldError(fieldName);

        // Notify validation result
        this.onValidate(fieldName, errors, this.getAllErrors());

        return errors.length === 0;
    }

    validateAll(showErrors = true) {
        const allErrors = [];

        for (const field of this.fields) {
            if (showErrors) {
                this.touched.add(field.name);
            }

            const isValid = this.validateField(field.name);
            if (!isValid) {
                allErrors.push({
                    field: field.name,
                    error: this.errors.get(field.name)
                });
            }
        }

        return {
            valid: allErrors.length === 0,
            errors: allErrors
        };
    }

    validateExpression(value) {
        if (!value) return null;

        // Check for balanced braces
        let braceCount = 0;
        for (const char of value) {
            if (char === '{') braceCount++;
            if (char === '}') braceCount--;
            if (braceCount < 0) {
                return 'Unmatched closing brace';
            }
        }
        if (braceCount !== 0) {
            return 'Unmatched opening brace';
        }

        // Check for valid expression syntax
        const exprPattern = /\$\{([^}]+)\}|#\{([^}]+)\}/g;
        let match;
        while ((match = exprPattern.exec(value)) !== null) {
            const expr = match[1] || match[2];
            // Basic validation - check for obvious syntax errors
            if (expr.trim() === '') {
                return 'Empty expression';
            }
        }

        return null;
    }

    runCustomValidator(validator, value, field) {
        switch (validator.type) {
            case 'regex':
                const regex = new RegExp(validator.pattern);
                if (!regex.test(value)) {
                    return validator.message || 'Invalid format';
                }
                break;

            case 'range':
                const num = parseFloat(value);
                if (validator.min !== undefined && num < validator.min) {
                    return validator.message || `Value must be at least ${validator.min}`;
                }
                if (validator.max !== undefined && num > validator.max) {
                    return validator.message || `Value must be at most ${validator.max}`;
                }
                break;

            case 'custom':
                if (validator.fn && typeof validator.fn === 'function') {
                    const error = validator.fn(value, field, this.values);
                    if (error) return error;
                }
                break;
        }

        return null;
    }

    isEmpty(value) {
        if (value === null || value === undefined) return true;
        if (typeof value === 'string' && value.trim() === '') return true;
        if (Array.isArray(value) && value.length === 0) return true;
        if (typeof value === 'object' && Object.keys(value).length === 0) return true;
        return false;
    }

    updateFieldError(fieldName) {
        const fieldDiv = this.container.querySelector(`.form-field[data-field="${fieldName}"]`);
        if (!fieldDiv) return;

        const error = this.errors.get(fieldName);
        const touched = this.touched.has(fieldName);
        const errorDiv = fieldDiv.querySelector('.invalid-feedback');
        const input = fieldDiv.querySelector('input, select, textarea');

        if (touched && error) {
            fieldDiv.classList.add('has-error');
            if (input) input.classList.add('is-invalid');
            if (errorDiv) {
                errorDiv.textContent = error;
                errorDiv.style.display = 'block';
            }
        } else {
            fieldDiv.classList.remove('has-error');
            if (input) input.classList.remove('is-invalid');
            if (errorDiv) {
                errorDiv.textContent = '';
                errorDiv.style.display = 'none';
            }

            // Show success state for touched valid fields
            if (touched && !this.isEmpty(this.values[fieldName])) {
                if (input) input.classList.add('is-valid');
            }
        }
    }

    getAllErrors() {
        const errors = [];
        this.errors.forEach((error, field) => {
            errors.push({ field, error });
        });
        return errors;
    }

    // ============================================
    // Utility Methods
    // ============================================

    formatJson(fieldName) {
        const textarea = this.container.querySelector(`textarea[name="${fieldName}"]`);
        if (!textarea) return;

        try {
            const parsed = JSON.parse(textarea.value);
            textarea.value = JSON.stringify(parsed, null, 2);
            this.showJsonStatus(fieldName, 'success', 'JSON is valid');
        } catch (e) {
            this.showJsonStatus(fieldName, 'error', 'Invalid JSON: ' + e.message);
        }
    }

    validateJson(fieldName) {
        const textarea = this.container.querySelector(`textarea[name="${fieldName}"]`);
        if (!textarea) return;

        try {
            JSON.parse(textarea.value);
            this.showJsonStatus(fieldName, 'success', 'JSON is valid');
        } catch (e) {
            this.showJsonStatus(fieldName, 'error', 'Invalid JSON: ' + e.message);
        }
    }

    showJsonStatus(fieldName, type, message) {
        const statusDiv = document.getElementById(`json-status-${fieldName}`);
        if (!statusDiv) return;

        const colorClass = type === 'success' ? 'text-success' : 'text-danger';
        const icon = type === 'success' ? 'check-circle' : 'x-circle';
        statusDiv.innerHTML = `<small class="${colorClass}"><i class="bi bi-${icon}"></i> ${escapeHtml(message)}</small>`;

        // Auto-hide after 3 seconds
        setTimeout(() => {
            statusDiv.innerHTML = '';
        }, 3000);
    }

    formatCode(fieldName, language) {
        const textarea = this.container.querySelector(`textarea[name="${fieldName}"]`);
        if (!textarea) return;

        // Basic formatting - in a real app, you'd use a proper formatter
        let code = textarea.value;

        if (language === 'javascript' || language === 'json') {
            try {
                // Try to format as JSON first
                const parsed = JSON.parse(code);
                textarea.value = JSON.stringify(parsed, null, 2);
            } catch {
                // Basic JS formatting - add newlines after semicolons and braces
                code = code
                    .replace(/;\s*/g, ';\n')
                    .replace(/{\s*/g, '{\n')
                    .replace(/}\s*/g, '\n}\n');
                textarea.value = code;
            }
        }
    }

    togglePassword(inputId) {
        const input = document.getElementById(inputId);
        if (!input) return;

        const button = input.nextElementSibling;
        if (input.type === 'password') {
            input.type = 'text';
            button.innerHTML = '<i class="bi bi-eye-slash"></i>';
        } else {
            input.type = 'password';
            button.innerHTML = '<i class="bi bi-eye"></i>';
        }
    }

    showExpressionHelper(fieldName) {
        // Show a modal with expression builder
        const modal = document.createElement('div');
        modal.className = 'modal fade';
        modal.id = 'expressionHelperModal';
        modal.innerHTML = `
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title"><i class="bi bi-braces"></i> Expression Helper</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <p class="text-muted">Build expressions to reference workflow data:</p>

                        <div class="mb-3">
                            <label class="form-label">Variable References</label>
                            <div class="list-group list-group-flush">
                                <button type="button" class="list-group-item list-group-item-action" onclick="formBuilder.insertExpression('${fieldName}', '\${input.}')">
                                    <code>\${input.fieldName}</code> - Input data field
                                </button>
                                <button type="button" class="list-group-item list-group-item-action" onclick="formBuilder.insertExpression('${fieldName}', '\${context.}')">
                                    <code>\${context.varName}</code> - Context variable
                                </button>
                                <button type="button" class="list-group-item list-group-item-action" onclick="formBuilder.insertExpression('${fieldName}', '\${env.}')">
                                    <code>\${env.VAR_NAME}</code> - Environment variable
                                </button>
                                <button type="button" class="list-group-item list-group-item-action" onclick="formBuilder.insertExpression('${fieldName}', '\${stepName.output.}')">
                                    <code>\${stepName.output.field}</code> - Previous step output
                                </button>
                            </div>
                        </div>

                        <div class="mb-3">
                            <label class="form-label">Expressions</label>
                            <div class="list-group list-group-flush">
                                <button type="button" class="list-group-item list-group-item-action" onclick="formBuilder.insertExpression('${fieldName}', '#{  }')">
                                    <code>#{expression}</code> - Evaluate expression
                                </button>
                            </div>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                    </div>
                </div>
            </div>
        `;

        document.body.appendChild(modal);
        const bsModal = new bootstrap.Modal(modal);
        bsModal.show();

        modal.addEventListener('hidden.bs.modal', () => {
            modal.remove();
        });
    }

    insertExpression(fieldName, expression) {
        const input = this.container.querySelector(`input[name="${fieldName}"], textarea[name="${fieldName}"]`);
        if (!input) return;

        const start = input.selectionStart;
        const end = input.selectionEnd;
        const text = input.value;

        input.value = text.substring(0, start) + expression + text.substring(end);
        input.focus();
        input.setSelectionRange(start + expression.length - 1, start + expression.length - 1);

        // Trigger change
        this.values[fieldName] = input.value;
        this.debouncedValidate(fieldName);
        this.onChange(fieldName, input.value, this.values);

        // Close modal
        const modal = document.getElementById('expressionHelperModal');
        if (modal) {
            bootstrap.Modal.getInstance(modal)?.hide();
        }
    }

    /**
     * Get all current form values
     */
    getValues() {
        return { ...this.values };
    }

    /**
     * Set form values programmatically
     */
    setValues(values) {
        this.values = { ...values };
        this.render();
    }

    /**
     * Update available steps for step reference fields
     */
    updateStepReferences(steps) {
        this.container.querySelectorAll('.step-ref-select').forEach(select => {
            const currentValue = select.value;
            select.innerHTML = '<option value="">-- Select Step --</option>';

            steps.forEach(step => {
                const option = document.createElement('option');
                option.value = step.id;
                option.textContent = step.name;
                option.selected = step.id === currentValue;
                select.appendChild(option);
            });
        });
    }
}

// Global instance
let formBuilder = new FormBuilder();

// Utility function (if not already defined)
if (typeof escapeHtml === 'undefined') {
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}
