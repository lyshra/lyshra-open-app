/**
 * Version Manager for Lyshra OpenApp Designer
 * Provides comprehensive version lifecycle management including:
 * - Version history timeline with state indicators
 * - Create new versions
 * - Activate/deprecate versions
 * - Rollback to previous versions
 * - Version comparison
 */

class VersionManager {
    constructor(options = {}) {
        this.workflowId = options.workflowId || null;
        this.workflowName = options.workflowName || 'Workflow';
        this.versions = [];
        this.selectedVersion = null;
        this.comparisonVersion = null;
        this.onVersionChange = options.onVersionChange || (() => {});
        this.container = null;
    }

    /**
     * Initialize the version manager with a container element
     */
    async init(container, workflowId) {
        this.container = container;
        this.workflowId = workflowId;
        await this.loadVersions();
        this.render();
    }

    /**
     * Load versions from API
     */
    async loadVersions() {
        try {
            this.versions = await api.getVersions(this.workflowId);
            // Sort by creation date descending (newest first)
            this.versions.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
        } catch (error) {
            console.error('Failed to load versions:', error);
            this.versions = [];
        }
    }

    /**
     * Render the version manager UI
     */
    render() {
        if (!this.container) return;

        const activeVersion = this.versions.find(v => v.state === 'ACTIVE');
        const draftVersions = this.versions.filter(v => v.state === 'DRAFT');
        const deprecatedVersions = this.versions.filter(v => v.state === 'DEPRECATED');
        const archivedVersions = this.versions.filter(v => v.state === 'ARCHIVED');

        this.container.innerHTML = `
            <div class="version-manager">
                <!-- Header with stats -->
                <div class="version-stats mb-4">
                    <div class="row g-3">
                        <div class="col-md-3">
                            <div class="stat-card stat-total">
                                <div class="stat-icon"><i class="bi bi-layers"></i></div>
                                <div class="stat-content">
                                    <div class="stat-value">${this.versions.length}</div>
                                    <div class="stat-label">Total Versions</div>
                                </div>
                            </div>
                        </div>
                        <div class="col-md-3">
                            <div class="stat-card stat-active">
                                <div class="stat-icon"><i class="bi bi-check-circle"></i></div>
                                <div class="stat-content">
                                    <div class="stat-value">${activeVersion ? '1' : '0'}</div>
                                    <div class="stat-label">Active</div>
                                </div>
                            </div>
                        </div>
                        <div class="col-md-3">
                            <div class="stat-card stat-draft">
                                <div class="stat-icon"><i class="bi bi-pencil"></i></div>
                                <div class="stat-content">
                                    <div class="stat-value">${draftVersions.length}</div>
                                    <div class="stat-label">Drafts</div>
                                </div>
                            </div>
                        </div>
                        <div class="col-md-3">
                            <div class="stat-card stat-deprecated">
                                <div class="stat-icon"><i class="bi bi-archive"></i></div>
                                <div class="stat-content">
                                    <div class="stat-value">${deprecatedVersions.length + archivedVersions.length}</div>
                                    <div class="stat-label">Deprecated/Archived</div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Action Buttons -->
                <div class="version-actions mb-4">
                    <button class="btn btn-primary" onclick="versionManager.showCreateVersionModal()">
                        <i class="bi bi-plus-circle"></i> Create New Version
                    </button>
                    ${activeVersion && deprecatedVersions.length > 0 ? `
                        <button class="btn btn-outline-warning" onclick="versionManager.showRollbackModal()">
                            <i class="bi bi-arrow-counterclockwise"></i> Rollback
                        </button>
                    ` : ''}
                    <button class="btn btn-outline-secondary" onclick="versionManager.refresh()">
                        <i class="bi bi-arrow-clockwise"></i> Refresh
                    </button>
                </div>

                <!-- Version Timeline -->
                <div class="version-timeline">
                    <h6 class="text-muted mb-3"><i class="bi bi-clock-history"></i> Version History</h6>
                    ${this.versions.length === 0 ? `
                        <div class="empty-state text-center py-5">
                            <i class="bi bi-layers display-4 text-muted"></i>
                            <p class="mt-3 text-muted">No versions yet. Create your first version to get started.</p>
                        </div>
                    ` : this.renderTimeline()}
                </div>
            </div>

            ${this.renderCreateVersionModal()}
            ${this.renderRollbackModal()}
            ${this.renderVersionDetailModal()}
            ${this.renderCompareModal()}
        `;

        this.attachEventListeners();
    }

    /**
     * Render the version timeline
     */
    renderTimeline() {
        return `
            <div class="timeline">
                ${this.versions.map((version, index) => this.renderTimelineItem(version, index)).join('')}
            </div>
        `;
    }

    /**
     * Render a single timeline item
     */
    renderTimelineItem(version, index) {
        const isActive = version.state === 'ACTIVE';
        const isDraft = version.state === 'DRAFT';
        const isDeprecated = version.state === 'DEPRECATED';
        const isArchived = version.state === 'ARCHIVED';

        const stateIcon = this.getStateIcon(version.state);
        const stateClass = this.getStateClass(version.state);

        return `
            <div class="timeline-item ${stateClass}" data-version-id="${version.id}">
                <div class="timeline-marker">
                    <i class="bi ${stateIcon}"></i>
                </div>
                <div class="timeline-content">
                    <div class="timeline-header">
                        <div class="d-flex align-items-center gap-2">
                            <strong class="version-number">${escapeHtml(version.versionNumber)}</strong>
                            <span class="badge ${getStatusBadgeClass(version.state)}">${version.state}</span>
                            ${isActive ? '<span class="badge bg-primary">Current</span>' : ''}
                        </div>
                        <div class="timeline-meta text-muted small">
                            <span><i class="bi bi-calendar"></i> ${formatDate(version.createdAt)}</span>
                            <span><i class="bi bi-person"></i> ${version.createdBy || 'System'}</span>
                        </div>
                    </div>
                    ${version.description ? `
                        <p class="timeline-description text-muted mb-2">${escapeHtml(version.description)}</p>
                    ` : ''}
                    <div class="timeline-details">
                        <span class="detail-badge">
                            <i class="bi bi-diagram-3"></i> ${version.steps?.length || 0} steps
                        </span>
                        <span class="detail-badge">
                            <i class="bi bi-arrow-right-circle"></i> ${Object.keys(version.connections || {}).length} connections
                        </span>
                        ${version.activatedAt ? `
                            <span class="detail-badge text-success">
                                <i class="bi bi-check-circle"></i> Activated ${formatRelativeTime(version.activatedAt)}
                            </span>
                        ` : ''}
                        ${version.deprecatedAt ? `
                            <span class="detail-badge text-warning">
                                <i class="bi bi-pause-circle"></i> Deprecated ${formatRelativeTime(version.deprecatedAt)}
                            </span>
                        ` : ''}
                    </div>
                    <div class="timeline-actions mt-2">
                        <button class="btn btn-sm btn-outline-secondary" onclick="versionManager.viewVersionDetails('${version.id}')">
                            <i class="bi bi-eye"></i> View
                        </button>
                        ${index < this.versions.length - 1 ? `
                            <button class="btn btn-sm btn-outline-secondary" onclick="versionManager.compareVersions('${version.id}', '${this.versions[index + 1].id}')">
                                <i class="bi bi-arrows-angle-expand"></i> Compare
                            </button>
                        ` : ''}
                        ${isDraft || isDeprecated ? `
                            <button class="btn btn-sm btn-outline-success" onclick="versionManager.activateVersion('${version.id}')">
                                <i class="bi bi-check-circle"></i> Activate
                            </button>
                        ` : ''}
                        ${isActive ? `
                            <button class="btn btn-sm btn-outline-warning" onclick="versionManager.deprecateVersion('${version.id}')">
                                <i class="bi bi-pause-circle"></i> Deprecate
                            </button>
                        ` : ''}
                        ${isDraft ? `
                            <button class="btn btn-sm btn-outline-danger" onclick="versionManager.deleteVersion('${version.id}')">
                                <i class="bi bi-trash"></i> Delete
                            </button>
                        ` : ''}
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Render Create Version Modal
     */
    renderCreateVersionModal() {
        const latestVersion = this.versions[0];
        const suggestedVersion = this.suggestNextVersion(latestVersion?.versionNumber);

        return `
            <div class="modal fade" id="createVersionModal" tabindex="-1">
                <div class="modal-dialog">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title"><i class="bi bi-plus-circle"></i> Create New Version</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body">
                            <form id="createVersionForm">
                                <div class="mb-3">
                                    <label class="form-label">Version Number *</label>
                                    <input type="text" class="form-control" id="newVersionNumber"
                                           value="${suggestedVersion}" required
                                           placeholder="e.g., 1.0.0, 2.1.0">
                                    <div class="form-text">Use semantic versioning (major.minor.patch)</div>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Description</label>
                                    <textarea class="form-control" id="newVersionDescription" rows="3"
                                              placeholder="Describe what changed in this version..."></textarea>
                                </div>
                                ${latestVersion ? `
                                    <div class="mb-3">
                                        <div class="form-check">
                                            <input class="form-check-input" type="checkbox" id="copyFromLatest" checked>
                                            <label class="form-check-label" for="copyFromLatest">
                                                Copy workflow from latest version (${latestVersion.versionNumber})
                                            </label>
                                        </div>
                                    </div>
                                ` : ''}
                                <div class="alert alert-info small">
                                    <i class="bi bi-info-circle"></i> New versions are created as DRAFT.
                                    You can edit and test before activating.
                                </div>
                            </form>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                            <button type="button" class="btn btn-primary" onclick="versionManager.createVersion()">
                                <i class="bi bi-plus-circle"></i> Create Version
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Render Rollback Modal
     */
    renderRollbackModal() {
        const rollbackTargets = this.versions.filter(v =>
            v.state === 'DEPRECATED' || v.state === 'DRAFT'
        );

        return `
            <div class="modal fade" id="rollbackModal" tabindex="-1">
                <div class="modal-dialog modal-lg">
                    <div class="modal-content">
                        <div class="modal-header bg-warning text-dark">
                            <h5 class="modal-title"><i class="bi bi-arrow-counterclockwise"></i> Rollback Version</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body">
                            <div class="alert alert-warning">
                                <i class="bi bi-exclamation-triangle"></i>
                                <strong>Warning:</strong> Rolling back will activate a previous version and deprecate the current active version.
                                All new executions will use the rolled back version.
                            </div>

                            <div class="mb-3">
                                <label class="form-label">Select Version to Rollback To *</label>
                                <select class="form-select" id="rollbackTargetVersion">
                                    <option value="">-- Select a version --</option>
                                    ${rollbackTargets.map(v => `
                                        <option value="${v.id}">
                                            ${v.versionNumber} (${v.state}) - ${formatDate(v.createdAt)}
                                        </option>
                                    `).join('')}
                                </select>
                            </div>

                            <div id="rollbackPreview" class="rollback-preview d-none">
                                <h6>Rollback Preview</h6>
                                <div class="rollback-comparison">
                                    <div class="comparison-item current">
                                        <div class="comparison-label">Current Active</div>
                                        <div class="comparison-content" id="rollbackCurrentVersion"></div>
                                    </div>
                                    <div class="comparison-arrow">
                                        <i class="bi bi-arrow-right"></i>
                                    </div>
                                    <div class="comparison-item target">
                                        <div class="comparison-label">Will Become Active</div>
                                        <div class="comparison-content" id="rollbackTargetVersionPreview"></div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                            <button type="button" class="btn btn-warning" id="btnConfirmRollback" onclick="versionManager.confirmRollback()" disabled>
                                <i class="bi bi-arrow-counterclockwise"></i> Confirm Rollback
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Render Version Detail Modal
     */
    renderVersionDetailModal() {
        return `
            <div class="modal fade" id="versionDetailModal" tabindex="-1">
                <div class="modal-dialog modal-xl">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title"><i class="bi bi-info-circle"></i> Version Details</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body" id="versionDetailContent">
                            <!-- Content loaded dynamically -->
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                            <button type="button" class="btn btn-primary" id="btnOpenInDesigner">
                                <i class="bi bi-pencil"></i> Open in Designer
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Render Compare Modal
     */
    renderCompareModal() {
        return `
            <div class="modal fade" id="compareVersionsModal" tabindex="-1">
                <div class="modal-dialog modal-xl">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title"><i class="bi bi-arrows-angle-expand"></i> Compare Versions</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body" id="compareVersionsContent">
                            <!-- Content loaded dynamically -->
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
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
        // Rollback target selection
        const rollbackSelect = document.getElementById('rollbackTargetVersion');
        if (rollbackSelect) {
            rollbackSelect.addEventListener('change', (e) => this.onRollbackTargetChange(e.target.value));
        }
    }

    /**
     * Show create version modal
     */
    showCreateVersionModal() {
        const modal = new bootstrap.Modal(document.getElementById('createVersionModal'));
        modal.show();
    }

    /**
     * Show rollback modal
     */
    showRollbackModal() {
        const modal = new bootstrap.Modal(document.getElementById('rollbackModal'));
        modal.show();
    }

    /**
     * Create a new version
     */
    async createVersion() {
        const versionNumber = document.getElementById('newVersionNumber').value.trim();
        const description = document.getElementById('newVersionDescription').value.trim();
        const copyFromLatest = document.getElementById('copyFromLatest')?.checked;

        if (!versionNumber) {
            alert('Please enter a version number');
            return;
        }

        // Check for duplicate version number
        if (this.versions.some(v => v.versionNumber === versionNumber)) {
            alert('A version with this number already exists');
            return;
        }

        try {
            let versionData = {
                versionNumber,
                description,
                steps: [],
                connections: {}
            };

            // Copy from latest version if selected
            if (copyFromLatest && this.versions.length > 0) {
                const latestVersion = this.versions[0];
                versionData.steps = latestVersion.steps || [];
                versionData.connections = latestVersion.connections || {};
                versionData.startStepId = latestVersion.startStepId;
                versionData.contextRetention = latestVersion.contextRetention;
                versionData.designerMetadata = latestVersion.designerMetadata;
            }

            await api.createVersion(this.workflowId, versionData);

            bootstrap.Modal.getInstance(document.getElementById('createVersionModal')).hide();
            this.showToast('Version created successfully', 'success');
            await this.refresh();
            this.onVersionChange();
        } catch (error) {
            alert('Failed to create version: ' + error.message);
        }
    }

    /**
     * Activate a version
     */
    async activateVersion(versionId) {
        const version = this.versions.find(v => v.id === versionId);
        if (!version) return;

        const activeVersion = this.versions.find(v => v.state === 'ACTIVE');
        let confirmMessage = `Activate version ${version.versionNumber}?`;

        if (activeVersion) {
            confirmMessage += `\n\nThis will deprecate the current active version (${activeVersion.versionNumber}).`;
        }

        if (!confirm(confirmMessage)) return;

        try {
            await api.activateVersion(versionId);
            this.showToast(`Version ${version.versionNumber} activated`, 'success');
            await this.refresh();
            this.onVersionChange();
        } catch (error) {
            alert('Failed to activate version: ' + error.message);
        }
    }

    /**
     * Deprecate a version
     */
    async deprecateVersion(versionId) {
        const version = this.versions.find(v => v.id === versionId);
        if (!version) return;

        if (!confirm(`Deprecate version ${version.versionNumber}?\n\nThis version will no longer be used for new executions.`)) return;

        try {
            await api.deprecateVersion(versionId);
            this.showToast(`Version ${version.versionNumber} deprecated`, 'warning');
            await this.refresh();
            this.onVersionChange();
        } catch (error) {
            alert('Failed to deprecate version: ' + error.message);
        }
    }

    /**
     * Delete a draft version
     */
    async deleteVersion(versionId) {
        const version = this.versions.find(v => v.id === versionId);
        if (!version) return;

        if (version.state !== 'DRAFT') {
            alert('Only draft versions can be deleted');
            return;
        }

        if (!confirm(`Delete version ${version.versionNumber}?\n\nThis action cannot be undone.`)) return;

        try {
            await api.delete(`/workflows/versions/${versionId}`);
            this.showToast(`Version ${version.versionNumber} deleted`, 'info');
            await this.refresh();
            this.onVersionChange();
        } catch (error) {
            alert('Failed to delete version: ' + error.message);
        }
    }

    /**
     * Handle rollback target selection change
     */
    onRollbackTargetChange(targetVersionId) {
        const preview = document.getElementById('rollbackPreview');
        const confirmBtn = document.getElementById('btnConfirmRollback');

        if (!targetVersionId) {
            preview.classList.add('d-none');
            confirmBtn.disabled = true;
            return;
        }

        const activeVersion = this.versions.find(v => v.state === 'ACTIVE');
        const targetVersion = this.versions.find(v => v.id === targetVersionId);

        if (!targetVersion) return;

        // Show preview
        preview.classList.remove('d-none');
        confirmBtn.disabled = false;

        document.getElementById('rollbackCurrentVersion').innerHTML = activeVersion ? `
            <div class="version-card">
                <strong>${activeVersion.versionNumber}</strong>
                <span class="badge bg-success">ACTIVE</span>
                <div class="small text-muted mt-1">
                    ${activeVersion.steps?.length || 0} steps |
                    Created ${formatRelativeTime(activeVersion.createdAt)}
                </div>
            </div>
        ` : '<em>No active version</em>';

        document.getElementById('rollbackTargetVersionPreview').innerHTML = `
            <div class="version-card">
                <strong>${targetVersion.versionNumber}</strong>
                <span class="badge ${getStatusBadgeClass(targetVersion.state)}">${targetVersion.state}</span>
                <div class="small text-muted mt-1">
                    ${targetVersion.steps?.length || 0} steps |
                    Created ${formatRelativeTime(targetVersion.createdAt)}
                </div>
            </div>
        `;

        this.selectedRollbackTarget = targetVersionId;
    }

    /**
     * Confirm rollback
     */
    async confirmRollback() {
        if (!this.selectedRollbackTarget) return;

        const targetVersion = this.versions.find(v => v.id === this.selectedRollbackTarget);
        if (!targetVersion) return;

        try {
            await api.rollbackToVersion(this.workflowId, this.selectedRollbackTarget);

            bootstrap.Modal.getInstance(document.getElementById('rollbackModal')).hide();
            this.showToast(`Rolled back to version ${targetVersion.versionNumber}`, 'success');
            await this.refresh();
            this.onVersionChange();
        } catch (error) {
            alert('Failed to rollback: ' + error.message);
        }
    }

    /**
     * View version details
     */
    async viewVersionDetails(versionId) {
        const version = this.versions.find(v => v.id === versionId);
        if (!version) return;

        const content = document.getElementById('versionDetailContent');
        content.innerHTML = `
            <div class="row">
                <div class="col-md-4">
                    <div class="card mb-3">
                        <div class="card-header">Version Info</div>
                        <div class="card-body">
                            <dl class="mb-0">
                                <dt>Version Number</dt>
                                <dd><strong>${escapeHtml(version.versionNumber)}</strong></dd>

                                <dt>State</dt>
                                <dd><span class="badge ${getStatusBadgeClass(version.state)}">${version.state}</span></dd>

                                <dt>Description</dt>
                                <dd>${version.description || '<em class="text-muted">No description</em>'}</dd>

                                <dt>Created</dt>
                                <dd>${formatDate(version.createdAt)} by ${version.createdBy || 'System'}</dd>

                                ${version.activatedAt ? `
                                    <dt>Activated</dt>
                                    <dd>${formatDate(version.activatedAt)} by ${version.activatedBy || 'System'}</dd>
                                ` : ''}

                                ${version.deprecatedAt ? `
                                    <dt>Deprecated</dt>
                                    <dd>${formatDate(version.deprecatedAt)} by ${version.deprecatedBy || 'System'}</dd>
                                ` : ''}
                            </dl>
                        </div>
                    </div>

                    <div class="card">
                        <div class="card-header">Statistics</div>
                        <div class="card-body">
                            <div class="d-flex justify-content-around text-center">
                                <div>
                                    <div class="h4 mb-0">${version.steps?.length || 0}</div>
                                    <small class="text-muted">Steps</small>
                                </div>
                                <div>
                                    <div class="h4 mb-0">${Object.keys(version.connections || {}).length}</div>
                                    <small class="text-muted">Connections</small>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="col-md-8">
                    <div class="card">
                        <div class="card-header">
                            <ul class="nav nav-tabs card-header-tabs" role="tablist">
                                <li class="nav-item">
                                    <a class="nav-link active" data-bs-toggle="tab" href="#stepsTab">
                                        <i class="bi bi-diagram-3"></i> Steps
                                    </a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link" data-bs-toggle="tab" href="#connectionsTab">
                                        <i class="bi bi-arrow-right-circle"></i> Connections
                                    </a>
                                </li>
                                <li class="nav-item">
                                    <a class="nav-link" data-bs-toggle="tab" href="#jsonTab">
                                        <i class="bi bi-code"></i> JSON
                                    </a>
                                </li>
                            </ul>
                        </div>
                        <div class="card-body">
                            <div class="tab-content">
                                <div class="tab-pane fade show active" id="stepsTab">
                                    ${this.renderStepsTable(version.steps)}
                                </div>
                                <div class="tab-pane fade" id="connectionsTab">
                                    ${this.renderConnectionsTable(version.connections)}
                                </div>
                                <div class="tab-pane fade" id="jsonTab">
                                    <pre class="code-block">${escapeHtml(JSON.stringify(version, null, 2))}</pre>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Set up open in designer button
        document.getElementById('btnOpenInDesigner').onclick = () => {
            window.location.href = `/designer?id=${this.workflowId}&version=${versionId}`;
        };

        const modal = new bootstrap.Modal(document.getElementById('versionDetailModal'));
        modal.show();
    }

    /**
     * Compare two versions
     */
    async compareVersions(versionId1, versionId2) {
        const version1 = this.versions.find(v => v.id === versionId1);
        const version2 = this.versions.find(v => v.id === versionId2);

        if (!version1 || !version2) return;

        const diff = this.computeVersionDiff(version1, version2);

        const content = document.getElementById('compareVersionsContent');
        content.innerHTML = `
            <div class="comparison-header mb-4">
                <div class="row">
                    <div class="col-md-5 text-center">
                        <strong>${version1.versionNumber}</strong>
                        <span class="badge ${getStatusBadgeClass(version1.state)}">${version1.state}</span>
                        <div class="small text-muted">${formatDate(version1.createdAt)}</div>
                    </div>
                    <div class="col-md-2 text-center">
                        <i class="bi bi-arrows-angle-expand display-6 text-muted"></i>
                    </div>
                    <div class="col-md-5 text-center">
                        <strong>${version2.versionNumber}</strong>
                        <span class="badge ${getStatusBadgeClass(version2.state)}">${version2.state}</span>
                        <div class="small text-muted">${formatDate(version2.createdAt)}</div>
                    </div>
                </div>
            </div>

            <div class="diff-summary alert ${diff.hasChanges ? 'alert-info' : 'alert-success'}">
                ${diff.hasChanges ? `
                    <i class="bi bi-exclamation-circle"></i>
                    <strong>${diff.added.length}</strong> steps added,
                    <strong>${diff.removed.length}</strong> steps removed,
                    <strong>${diff.modified.length}</strong> steps modified
                ` : `
                    <i class="bi bi-check-circle"></i>
                    No differences found between these versions
                `}
            </div>

            ${diff.hasChanges ? `
                <div class="row">
                    <div class="col-md-4">
                        <div class="card border-success mb-3">
                            <div class="card-header bg-success text-white">
                                <i class="bi bi-plus-circle"></i> Added Steps (${diff.added.length})
                            </div>
                            <ul class="list-group list-group-flush">
                                ${diff.added.length === 0 ? '<li class="list-group-item text-muted">No steps added</li>' : ''}
                                ${diff.added.map(step => `
                                    <li class="list-group-item">
                                        <strong>${escapeHtml(step.name)}</strong>
                                        <div class="small text-muted">${step.processor?.processorName || 'Unknown'}</div>
                                    </li>
                                `).join('')}
                            </ul>
                        </div>
                    </div>
                    <div class="col-md-4">
                        <div class="card border-danger mb-3">
                            <div class="card-header bg-danger text-white">
                                <i class="bi bi-dash-circle"></i> Removed Steps (${diff.removed.length})
                            </div>
                            <ul class="list-group list-group-flush">
                                ${diff.removed.length === 0 ? '<li class="list-group-item text-muted">No steps removed</li>' : ''}
                                ${diff.removed.map(step => `
                                    <li class="list-group-item">
                                        <strong>${escapeHtml(step.name)}</strong>
                                        <div class="small text-muted">${step.processor?.processorName || 'Unknown'}</div>
                                    </li>
                                `).join('')}
                            </ul>
                        </div>
                    </div>
                    <div class="col-md-4">
                        <div class="card border-warning mb-3">
                            <div class="card-header bg-warning text-dark">
                                <i class="bi bi-pencil"></i> Modified Steps (${diff.modified.length})
                            </div>
                            <ul class="list-group list-group-flush">
                                ${diff.modified.length === 0 ? '<li class="list-group-item text-muted">No steps modified</li>' : ''}
                                ${diff.modified.map(item => `
                                    <li class="list-group-item">
                                        <strong>${escapeHtml(item.step.name)}</strong>
                                        <div class="small text-muted">
                                            Changed: ${item.changes.join(', ')}
                                        </div>
                                    </li>
                                `).join('')}
                            </ul>
                        </div>
                    </div>
                </div>
            ` : ''}
        `;

        const modal = new bootstrap.Modal(document.getElementById('compareVersionsModal'));
        modal.show();
    }

    /**
     * Compute differences between two versions
     */
    computeVersionDiff(version1, version2) {
        const steps1 = version1.steps || [];
        const steps2 = version2.steps || [];

        const steps1Map = new Map(steps1.map(s => [s.id, s]));
        const steps2Map = new Map(steps2.map(s => [s.id, s]));

        const added = [];
        const removed = [];
        const modified = [];

        // Find added and modified steps
        steps1.forEach(step => {
            const oldStep = steps2Map.get(step.id);
            if (!oldStep) {
                added.push(step);
            } else {
                const changes = this.compareSteps(step, oldStep);
                if (changes.length > 0) {
                    modified.push({ step, changes });
                }
            }
        });

        // Find removed steps
        steps2.forEach(step => {
            if (!steps1Map.has(step.id)) {
                removed.push(step);
            }
        });

        return {
            hasChanges: added.length > 0 || removed.length > 0 || modified.length > 0,
            added,
            removed,
            modified
        };
    }

    /**
     * Compare two steps and return list of changes
     */
    compareSteps(step1, step2) {
        const changes = [];

        if (step1.name !== step2.name) changes.push('name');
        if (step1.processor?.processorName !== step2.processor?.processorName) changes.push('processor');
        if (JSON.stringify(step1.inputConfig) !== JSON.stringify(step2.inputConfig)) changes.push('configuration');
        if (JSON.stringify(step1.position) !== JSON.stringify(step2.position)) changes.push('position');

        return changes;
    }

    /**
     * Render steps table
     */
    renderStepsTable(steps) {
        if (!steps || steps.length === 0) {
            return '<p class="text-muted">No steps in this version</p>';
        }

        return `
            <table class="table table-sm">
                <thead>
                    <tr>
                        <th>Step Name</th>
                        <th>Processor</th>
                        <th>Type</th>
                    </tr>
                </thead>
                <tbody>
                    ${steps.map(step => `
                        <tr>
                            <td><strong>${escapeHtml(step.name)}</strong></td>
                            <td><code>${step.processor?.processorName || '-'}</code></td>
                            <td><span class="badge bg-secondary">${step.type || 'PROCESSOR'}</span></td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;
    }

    /**
     * Render connections table
     */
    renderConnectionsTable(connections) {
        if (!connections || Object.keys(connections).length === 0) {
            return '<p class="text-muted">No connections in this version</p>';
        }

        const connList = Object.values(connections);
        return `
            <table class="table table-sm">
                <thead>
                    <tr>
                        <th>From</th>
                        <th>To</th>
                        <th>Type</th>
                        <th>Label</th>
                    </tr>
                </thead>
                <tbody>
                    ${connList.map(conn => `
                        <tr>
                            <td><code>${conn.sourceStepId}</code></td>
                            <td><code>${conn.targetStepId}</code></td>
                            <td><span class="badge bg-secondary">${conn.type || 'DEFAULT'}</span></td>
                            <td>${conn.label || '-'}</td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;
    }

    /**
     * Refresh versions
     */
    async refresh() {
        await this.loadVersions();
        this.render();
    }

    /**
     * Get state icon
     */
    getStateIcon(state) {
        const icons = {
            'DRAFT': 'bi-pencil',
            'ACTIVE': 'bi-check-circle-fill',
            'DEPRECATED': 'bi-pause-circle',
            'ARCHIVED': 'bi-archive'
        };
        return icons[state] || 'bi-circle';
    }

    /**
     * Get state class
     */
    getStateClass(state) {
        const classes = {
            'DRAFT': 'timeline-draft',
            'ACTIVE': 'timeline-active',
            'DEPRECATED': 'timeline-deprecated',
            'ARCHIVED': 'timeline-archived'
        };
        return classes[state] || '';
    }

    /**
     * Suggest next version number
     */
    suggestNextVersion(currentVersion) {
        if (!currentVersion) return '1.0.0';

        const parts = currentVersion.split('.');
        if (parts.length === 3) {
            const patch = parseInt(parts[2]) || 0;
            return `${parts[0]}.${parts[1]}.${patch + 1}`;
        }

        return `${currentVersion}.1`;
    }

    /**
     * Show toast notification
     */
    showToast(message, type = 'info') {
        // Create toast container if not exists
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

        // Remove from DOM after hiding
        toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
    }
}

// Global instance
let versionManager;
