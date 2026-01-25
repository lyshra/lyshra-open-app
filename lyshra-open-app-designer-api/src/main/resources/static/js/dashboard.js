/**
 * Dashboard functionality for Lyshra OpenApp Designer
 */

async function loadDashboard() {
    try {
        const [workflows, stats, executions] = await Promise.all([
            api.getWorkflows(),
            api.getStatistics(),
            api.getExecutions()
        ]);

        document.getElementById('totalWorkflows').textContent = workflows.length;
        document.getElementById('runningExecutions').textContent = stats.runningExecutions;
        document.getElementById('completedToday').textContent = stats.executionsToday;
        document.getElementById('successRate').textContent = stats.successRate.toFixed(1) + '%';

        renderRecentExecutions(executions.slice(0, 10));
        renderActiveWorkflows(workflows.filter(w => w.lifecycleState === 'ACTIVE').slice(0, 5));
    } catch (error) {
        console.error('Failed to load dashboard:', error);
    }
}

function renderRecentExecutions(executions) {
    const tbody = document.getElementById('recentExecutions');

    if (!executions || executions.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">No executions found</td></tr>';
        return;
    }

    tbody.innerHTML = executions.map(exec => `
        <tr onclick="window.location.href='/executions?id=${exec.id}'" style="cursor: pointer;">
            <td>${escapeHtml(exec.workflowName)}</td>
            <td><span class="badge bg-secondary">${exec.versionNumber}</span></td>
            <td><span class="badge ${getStatusBadgeClass(exec.status)}">${exec.status}</span></td>
            <td>${formatRelativeTime(exec.startedAt)}</td>
            <td>${exec.completedAt ? formatDuration(new Date(exec.completedAt) - new Date(exec.startedAt)) : '-'}</td>
        </tr>
    `).join('');
}

function renderActiveWorkflows(workflows) {
    const container = document.getElementById('activeWorkflows');

    if (!workflows || workflows.length === 0) {
        container.innerHTML = '<div class="list-group-item text-center text-muted">No active workflows</div>';
        return;
    }

    container.innerHTML = workflows.map(wf => `
        <a href="/designer?id=${wf.id}" class="list-group-item list-group-item-action d-flex justify-content-between align-items-center">
            <div>
                <div class="fw-semibold">${escapeHtml(wf.name)}</div>
                <small class="text-muted">${wf.organization}/${wf.module}</small>
            </div>
            <span class="badge ${getStatusBadgeClass(wf.lifecycleState)}">${wf.lifecycleState}</span>
        </a>
    `).join('');
}

document.addEventListener('DOMContentLoaded', loadDashboard);

setInterval(loadDashboard, 30000);
