/**
 * Executions list functionality for Lyshra OpenApp Designer
 */

let autoRefreshInterval;
let currentExecutionId = null;

async function loadExecutions() {
    try {
        const [executions, stats] = await Promise.all([
            api.getExecutions(),
            api.getStatistics()
        ]);

        updateStatistics(stats);
        renderExecutions(executions);
    } catch (error) {
        console.error('Failed to load executions:', error);
    }
}

function updateStatistics(stats) {
    document.getElementById('statTotal').textContent = stats.totalExecutions;
    document.getElementById('statRunning').textContent = stats.runningExecutions;
    document.getElementById('statCompleted').textContent = stats.completedExecutions;
    document.getElementById('statFailed').textContent = stats.failedExecutions;
    document.getElementById('statCancelled').textContent = stats.cancelledExecutions;
    document.getElementById('statSuccessRate').textContent = stats.successRate.toFixed(1) + '%';
}

function renderExecutions(executions) {
    const tbody = document.getElementById('executionTable');

    if (!executions || executions.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">No executions found</td></tr>';
        return;
    }

    tbody.innerHTML = executions.map(exec => `
        <tr onclick="showExecutionDetails('${exec.id}')" style="cursor: pointer;">
            <td><code class="small">${exec.id.substring(0, 8)}...</code></td>
            <td>${escapeHtml(exec.workflowName)}</td>
            <td><span class="badge bg-secondary">${exec.versionNumber}</span></td>
            <td><span class="badge ${getStatusBadgeClass(exec.status)}">${exec.status}</span></td>
            <td>${formatDate(exec.startedAt)}</td>
            <td>${exec.completedAt ? formatDuration(new Date(exec.completedAt) - new Date(exec.startedAt)) : '-'}</td>
            <td>${exec.triggeredBy || '-'}</td>
            <td>
                ${exec.status === 'RUNNING' || exec.status === 'PENDING' ? `
                    <button class="btn btn-sm btn-outline-warning" onclick="event.stopPropagation(); cancelExecution('${exec.id}')">
                        <i class="bi bi-stop-circle"></i>
                    </button>
                ` : ''}
                ${exec.status === 'FAILED' ? `
                    <button class="btn btn-sm btn-outline-primary" onclick="event.stopPropagation(); retryExecution('${exec.id}')">
                        <i class="bi bi-arrow-repeat"></i>
                    </button>
                ` : ''}
            </td>
        </tr>
    `).join('');
}

async function showExecutionDetails(executionId) {
    try {
        currentExecutionId = executionId;
        const exec = await api.getExecution(executionId);
        const modal = document.getElementById('executionModal');
        const body = document.getElementById('executionDetails');

        body.innerHTML = `
            <div class="row mb-4">
                <div class="col-md-6">
                    <h6>General Information</h6>
                    <table class="table table-sm">
                        <tr><th>ID</th><td><code>${exec.id}</code></td></tr>
                        <tr><th>Workflow</th><td>${escapeHtml(exec.workflowName)}</td></tr>
                        <tr><th>Version</th><td>${exec.versionNumber}</td></tr>
                        <tr><th>Status</th><td><span class="badge ${getStatusBadgeClass(exec.status)}">${exec.status}</span></td></tr>
                        <tr><th>Triggered By</th><td>${exec.triggeredBy || '-'}</td></tr>
                        <tr><th>Correlation ID</th><td>${exec.correlationId || '-'}</td></tr>
                    </table>
                </div>
                <div class="col-md-6">
                    <h6>Timing</h6>
                    <table class="table table-sm">
                        <tr><th>Started</th><td>${formatDate(exec.startedAt)}</td></tr>
                        <tr><th>Completed</th><td>${exec.completedAt ? formatDate(exec.completedAt) : '-'}</td></tr>
                        <tr><th>Duration</th><td>${exec.completedAt ? formatDuration(new Date(exec.completedAt) - new Date(exec.startedAt)) : '-'}</td></tr>
                        <tr><th>Current Step</th><td>${exec.currentStepName || '-'}</td></tr>
                    </table>
                </div>
            </div>

            ${exec.errorMessage ? `
                <div class="alert alert-danger">
                    <h6><i class="bi bi-exclamation-triangle"></i> Error</h6>
                    <p class="mb-1"><strong>${exec.errorCode || 'ERROR'}</strong></p>
                    <p class="mb-0">${escapeHtml(exec.errorMessage)}</p>
                </div>
            ` : ''}

            <h6>Input Data</h6>
            <pre class="bg-light p-3 rounded small">${JSON.stringify(exec.inputData || {}, null, 2)}</pre>

            ${exec.outputData ? `
                <h6>Output Data</h6>
                <pre class="bg-light p-3 rounded small">${JSON.stringify(exec.outputData, null, 2)}</pre>
            ` : ''}

            ${exec.stepLogs && exec.stepLogs.length > 0 ? `
                <h6>Step Execution Log</h6>
                <div class="table-responsive">
                    <table class="table table-sm">
                        <thead>
                            <tr>
                                <th>Step</th>
                                <th>Status</th>
                                <th>Started</th>
                                <th>Duration</th>
                                <th>Branch</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${exec.stepLogs.map(log => `
                                <tr class="${log.status === 'FAILED' ? 'table-danger' : ''}">
                                    <td>${escapeHtml(log.stepName)}</td>
                                    <td><span class="badge ${getStatusBadgeClass(log.status)}">${log.status}</span></td>
                                    <td>${formatDate(log.startedAt)}</td>
                                    <td>${log.completedAt ? formatDuration(new Date(log.completedAt) - new Date(log.startedAt)) : '-'}</td>
                                    <td>${log.branch || '-'}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            ` : ''}
        `;

        const cancelBtn = document.getElementById('btnCancelExecution');
        const retryBtn = document.getElementById('btnRetryExecution');

        cancelBtn.style.display = (exec.status === 'RUNNING' || exec.status === 'PENDING') ? '' : 'none';
        retryBtn.style.display = exec.status === 'FAILED' ? '' : 'none';

        new bootstrap.Modal(modal).show();
    } catch (error) {
        alert('Failed to load execution details: ' + error.message);
    }
}

async function cancelExecution(id) {
    if (!confirm('Cancel this execution?')) return;
    try {
        await api.cancelExecution(id);
        loadExecutions();
        if (currentExecutionId === id) {
            showExecutionDetails(id);
        }
    } catch (error) {
        alert('Failed to cancel execution: ' + error.message);
    }
}

async function retryExecution(id) {
    if (!confirm('Retry this execution?')) return;
    try {
        const newExec = await api.retryExecution(id);
        loadExecutions();
        showExecutionDetails(newExec.id);
    } catch (error) {
        alert('Failed to retry execution: ' + error.message);
    }
}

function startAutoRefresh() {
    autoRefreshInterval = setInterval(loadExecutions, 5000);
}

function stopAutoRefresh() {
    if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval);
        autoRefreshInterval = null;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    loadExecutions();

    document.getElementById('autoRefresh').addEventListener('change', (e) => {
        if (e.target.checked) {
            startAutoRefresh();
        } else {
            stopAutoRefresh();
        }
    });

    document.getElementById('filterStatus').addEventListener('change', loadExecutions);
    document.getElementById('filterWorkflow').addEventListener('change', loadExecutions);

    document.getElementById('btnCancelExecution').addEventListener('click', () => {
        if (currentExecutionId) cancelExecution(currentExecutionId);
    });

    document.getElementById('btnRetryExecution').addEventListener('click', () => {
        if (currentExecutionId) retryExecution(currentExecutionId);
    });

    if (document.getElementById('autoRefresh').checked) {
        startAutoRefresh();
    }
});
