/**
 * Workflows list functionality for Lyshra OpenApp Designer
 */

let allWorkflows = [];

async function loadWorkflows() {
    try {
        allWorkflows = await api.getWorkflows();
        renderWorkflows(allWorkflows);
    } catch (error) {
        console.error('Failed to load workflows:', error);
        document.getElementById('workflowGrid').innerHTML = `
            <div class="col-12 text-center py-5 text-danger">
                <i class="bi bi-exclamation-circle display-4"></i>
                <p class="mt-3">Failed to load workflows</p>
            </div>
        `;
    }
}

function renderWorkflows(workflows) {
    const grid = document.getElementById('workflowGrid');

    if (!workflows || workflows.length === 0) {
        grid.innerHTML = `
            <div class="col-12 text-center py-5">
                <i class="bi bi-inbox display-4 text-muted"></i>
                <p class="mt-3 text-muted">No workflows found</p>
                <a href="/designer" class="btn btn-primary">Create your first workflow</a>
            </div>
        `;
        return;
    }

    grid.innerHTML = workflows.map(wf => `
        <div class="col-md-4 col-lg-3 mb-4">
            <div class="card workflow-card h-100">
                <div class="card-header d-flex justify-content-between align-items-center">
                    <span class="badge ${getStatusBadgeClass(wf.lifecycleState)}">${wf.lifecycleState}</span>
                    <div class="dropdown">
                        <button class="btn btn-sm btn-link text-muted" data-bs-toggle="dropdown">
                            <i class="bi bi-three-dots-vertical"></i>
                        </button>
                        <ul class="dropdown-menu dropdown-menu-end">
                            <li><a class="dropdown-item" href="/designer?id=${wf.id}">
                                <i class="bi bi-pencil"></i> Edit</a></li>
                            <li><a class="dropdown-item" href="#" onclick="showVersions('${wf.id}')">
                                <i class="bi bi-clock-history"></i> Versions</a></li>
                            <li><a class="dropdown-item" href="#" onclick="duplicateWorkflow('${wf.id}')">
                                <i class="bi bi-copy"></i> Duplicate</a></li>
                            ${wf.lifecycleState === 'ACTIVE' ? `
                                <li><a class="dropdown-item text-success" href="/execute?workflow=${wf.id}">
                                    <i class="bi bi-play-fill"></i> Execute</a></li>
                            ` : ''}
                            <li><hr class="dropdown-divider"></li>
                            ${wf.lifecycleState === 'DRAFT' ? `
                                <li><a class="dropdown-item text-success" href="#" onclick="activateWorkflow('${wf.id}')">
                                    <i class="bi bi-check-circle"></i> Activate</a></li>
                            ` : ''}
                            ${wf.lifecycleState === 'ACTIVE' ? `
                                <li><a class="dropdown-item text-warning" href="#" onclick="deprecateWorkflow('${wf.id}')">
                                    <i class="bi bi-pause-circle"></i> Deprecate</a></li>
                            ` : ''}
                            <li><a class="dropdown-item text-danger" href="#" onclick="deleteWorkflow('${wf.id}')">
                                <i class="bi bi-trash"></i> Delete</a></li>
                        </ul>
                    </div>
                </div>
                <div class="card-body">
                    <h5 class="card-title">${escapeHtml(wf.name)}</h5>
                    <p class="card-text text-muted small">${wf.description || 'No description'}</p>
                    <div class="small text-muted">
                        <i class="bi bi-folder"></i> ${wf.organization}/${wf.module}
                    </div>
                </div>
                <div class="card-footer text-muted small d-flex justify-content-between">
                    <span><i class="bi bi-clock"></i> ${formatRelativeTime(wf.updatedAt)}</span>
                    <span><i class="bi bi-person"></i> ${wf.createdBy}</span>
                </div>
            </div>
        </div>
    `).join('');
}

function filterWorkflows() {
    const search = document.getElementById('searchWorkflows').value.toLowerCase();
    const state = document.getElementById('filterState').value;
    const org = document.getElementById('filterOrganization').value;

    let filtered = allWorkflows;

    if (search) {
        filtered = filtered.filter(wf =>
            wf.name.toLowerCase().includes(search) ||
            (wf.description && wf.description.toLowerCase().includes(search))
        );
    }

    if (state) {
        filtered = filtered.filter(wf => wf.lifecycleState === state);
    }

    if (org) {
        filtered = filtered.filter(wf => wf.organization === org);
    }

    const sortBy = document.getElementById('sortBy').value;
    filtered.sort((a, b) => {
        if (sortBy === 'name') return a.name.localeCompare(b.name);
        if (sortBy === 'createdAt') return new Date(b.createdAt) - new Date(a.createdAt);
        return new Date(b.updatedAt) - new Date(a.updatedAt);
    });

    renderWorkflows(filtered);
}

async function showVersions(workflowId) {
    try {
        const workflow = allWorkflows.find(w => w.id === workflowId);
        const modal = document.getElementById('versionModal');
        const body = document.getElementById('versionModalBody');

        // Set up full page link
        document.getElementById('btnOpenVersionPage').href = `/versions?id=${workflowId}`;

        // Show loading state
        body.innerHTML = `
            <div class="text-center py-4">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">Loading...</span>
                </div>
            </div>
        `;

        new bootstrap.Modal(modal).show();

        // Initialize version manager in modal
        if (!window.modalVersionManager) {
            window.modalVersionManager = new VersionManager({
                workflowId: workflowId,
                workflowName: workflow?.name || 'Workflow',
                onVersionChange: () => {
                    loadWorkflows();
                }
            });
        } else {
            window.modalVersionManager.workflowId = workflowId;
            window.modalVersionManager.workflowName = workflow?.name || 'Workflow';
        }

        await window.modalVersionManager.init(body, workflowId);
    } catch (error) {
        console.error('Failed to load versions:', error);
        document.getElementById('versionModalBody').innerHTML = `
            <div class="alert alert-danger">
                <i class="bi bi-exclamation-triangle"></i> Failed to load versions: ${error.message}
            </div>
        `;
    }
}

async function duplicateWorkflow(id) {
    const newName = prompt('Enter name for the duplicate:');
    if (!newName) return;

    try {
        await api.duplicateWorkflow(id, newName);
        loadWorkflows();
    } catch (error) {
        alert('Failed to duplicate workflow: ' + error.message);
    }
}

async function activateWorkflow(id) {
    if (!confirm('Activate this workflow?')) return;
    try {
        await api.changeWorkflowState(id, 'ACTIVE');
        loadWorkflows();
    } catch (error) {
        alert('Failed to activate workflow: ' + error.message);
    }
}

async function deprecateWorkflow(id) {
    if (!confirm('Deprecate this workflow?')) return;
    try {
        await api.changeWorkflowState(id, 'DEPRECATED');
        loadWorkflows();
    } catch (error) {
        alert('Failed to deprecate workflow: ' + error.message);
    }
}

async function deleteWorkflow(id) {
    if (!confirm('Are you sure you want to delete this workflow? This action cannot be undone.')) return;
    try {
        await api.deleteWorkflow(id);
        loadWorkflows();
    } catch (error) {
        alert('Failed to delete workflow: ' + error.message);
    }
}

async function activateVersion(versionId) {
    if (!confirm('Activate this version?')) return;
    try {
        await api.activateVersion(versionId);
        bootstrap.Modal.getInstance(document.getElementById('versionModal')).hide();
        loadWorkflows();
    } catch (error) {
        alert('Failed to activate version: ' + error.message);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    loadWorkflows();

    document.getElementById('searchWorkflows').addEventListener('input', filterWorkflows);
    document.getElementById('filterState').addEventListener('change', filterWorkflows);
    document.getElementById('filterOrganization').addEventListener('change', filterWorkflows);
    document.getElementById('sortBy').addEventListener('change', filterWorkflows);
    document.getElementById('btnRefresh').addEventListener('click', loadWorkflows);
});
