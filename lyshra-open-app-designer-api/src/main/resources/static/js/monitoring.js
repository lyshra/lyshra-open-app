/**
 * Monitoring dashboard functionality for Lyshra OpenApp Designer
 */

let eventSource = null;

async function loadMonitoringData() {
    try {
        const [stats, running, executions, workflows] = await Promise.all([
            api.getStatistics(),
            api.getRunningExecutions(),
            api.getExecutions(),
            api.getWorkflows()
        ]);

        updateStatistics(stats);
        renderRunningExecutions(running);
        renderActivityFeed(executions.slice(0, 20));
        renderWorkflowPerformance(workflows, executions);
    } catch (error) {
        console.error('Failed to load monitoring data:', error);
    }
}

function updateStatistics(stats) {
    document.getElementById('todayCount').textContent = stats.executionsToday;
    document.getElementById('weekCount').textContent = stats.executionsThisWeek;
    document.getElementById('avgDuration').textContent = formatDuration(stats.averageDuration?.toMillis?.() || stats.averageDuration);
    document.getElementById('minDuration').textContent = formatDuration(stats.minDuration?.toMillis?.() || stats.minDuration);
    document.getElementById('maxDuration').textContent = formatDuration(stats.maxDuration?.toMillis?.() || stats.maxDuration);
    document.getElementById('overallSuccessRate').textContent = stats.successRate.toFixed(1) + '%';

    const total = stats.completedExecutions + stats.failedExecutions;
    if (total > 0) {
        const successPercent = (stats.completedExecutions / total * 100).toFixed(0);
        const failurePercent = (stats.failedExecutions / total * 100).toFixed(0);
        document.getElementById('todaySuccessBar').style.width = successPercent + '%';
        document.getElementById('todayFailureBar').style.width = failurePercent + '%';
    }
}

function renderRunningExecutions(executions) {
    const container = document.getElementById('runningExecutionsList');
    document.getElementById('runningCount').textContent = executions.length;

    if (!executions || executions.length === 0) {
        container.innerHTML = '<p class="text-muted text-center py-4">No running executions</p>';
        return;
    }

    container.innerHTML = executions.map(exec => `
        <div class="running-execution-card">
            <div class="d-flex justify-content-between align-items-start mb-2">
                <div>
                    <strong>${escapeHtml(exec.workflowName)}</strong>
                    <span class="badge bg-secondary ms-2">${exec.versionNumber}</span>
                </div>
                <span class="badge ${getStatusBadgeClass(exec.status)} pulse">${exec.status}</span>
            </div>
            <div class="small text-muted mb-2">
                <i class="bi bi-clock"></i> Started ${formatRelativeTime(exec.startedAt)}
                ${exec.currentStepName ? `| <i class="bi bi-cursor"></i> ${exec.currentStepName}` : ''}
            </div>
            <div class="progress">
                <div class="progress-bar progress-bar-striped progress-bar-animated" style="width: 60%"></div>
            </div>
        </div>
    `).join('');
}

function renderActivityFeed(executions) {
    const container = document.getElementById('activityFeed');

    if (!executions || executions.length === 0) {
        container.innerHTML = '<p class="text-muted text-center py-4">No recent activity</p>';
        return;
    }

    container.innerHTML = executions.map(exec => {
        let activityClass = '';
        let icon = '';

        switch (exec.status) {
            case 'COMPLETED':
                activityClass = 'success';
                icon = 'check-circle-fill text-success';
                break;
            case 'FAILED':
            case 'ABORTED':
                activityClass = 'error';
                icon = 'x-circle-fill text-danger';
                break;
            case 'RUNNING':
            case 'PENDING':
                activityClass = 'info';
                icon = 'play-circle-fill text-primary';
                break;
            case 'CANCELLED':
                activityClass = 'warning';
                icon = 'pause-circle-fill text-warning';
                break;
            default:
                icon = 'circle';
        }

        return `
            <div class="activity-item ${activityClass}">
                <div class="d-flex justify-content-between">
                    <div>
                        <i class="bi bi-${icon}"></i>
                        <strong>${escapeHtml(exec.workflowName)}</strong>
                        <span class="badge ${getStatusBadgeClass(exec.status)}">${exec.status}</span>
                    </div>
                    <span class="activity-time">${formatRelativeTime(exec.startedAt)}</span>
                </div>
                ${exec.errorMessage ? `<div class="small text-danger mt-1">${escapeHtml(exec.errorMessage)}</div>` : ''}
            </div>
        `;
    }).join('');
}

function renderWorkflowPerformance(workflows, executions) {
    const tbody = document.getElementById('workflowPerformanceTable');

    const workflowStats = new Map();
    workflows.forEach(wf => {
        workflowStats.set(wf.id, {
            id: wf.id,
            name: wf.name,
            state: wf.lifecycleState,
            totalExecutions: 0,
            completedExecutions: 0,
            failedExecutions: 0,
            totalDuration: 0,
            lastExecution: null
        });
    });

    executions.forEach(exec => {
        const stats = workflowStats.get(exec.workflowDefinitionId);
        if (stats) {
            stats.totalExecutions++;
            if (exec.status === 'COMPLETED') {
                stats.completedExecutions++;
                if (exec.completedAt && exec.startedAt) {
                    stats.totalDuration += new Date(exec.completedAt) - new Date(exec.startedAt);
                }
            } else if (exec.status === 'FAILED' || exec.status === 'ABORTED') {
                stats.failedExecutions++;
            }
            if (!stats.lastExecution || new Date(exec.startedAt) > new Date(stats.lastExecution)) {
                stats.lastExecution = exec.startedAt;
            }
        }
    });

    const sortedStats = Array.from(workflowStats.values())
        .filter(s => s.totalExecutions > 0)
        .sort((a, b) => b.totalExecutions - a.totalExecutions)
        .slice(0, 10);

    if (sortedStats.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No execution data available</td></tr>';
        return;
    }

    tbody.innerHTML = sortedStats.map(stats => {
        const successRate = stats.totalExecutions > 0
            ? (stats.completedExecutions / stats.totalExecutions * 100).toFixed(1)
            : 0;
        const avgDuration = stats.completedExecutions > 0
            ? stats.totalDuration / stats.completedExecutions
            : 0;

        return `
            <tr>
                <td>
                    <a href="/workflows?id=${stats.id}">${escapeHtml(stats.name)}</a>
                </td>
                <td>${stats.totalExecutions}</td>
                <td>
                    <div class="d-flex align-items-center">
                        <div class="progress flex-grow-1 me-2" style="height: 8px;">
                            <div class="progress-bar bg-success" style="width: ${successRate}%"></div>
                        </div>
                        <span class="small">${successRate}%</span>
                    </div>
                </td>
                <td>${formatDuration(avgDuration)}</td>
                <td>${stats.lastExecution ? formatRelativeTime(stats.lastExecution) : '-'}</td>
                <td><span class="badge ${getStatusBadgeClass(stats.state)}">${stats.state}</span></td>
            </tr>
        `;
    }).join('');
}

function connectToEventStream() {
    const token = getToken();
    if (!token) return;

    eventSource = new EventSource(`/api/v1/executions/stream`);

    eventSource.onmessage = (event) => {
        const execution = JSON.parse(event.data);
        handleExecutionUpdate(execution);
    };

    eventSource.onerror = () => {
        document.getElementById('connectionStatus').innerHTML = '<i class="bi bi-broadcast"></i> Disconnected';
        document.getElementById('connectionStatus').className = 'badge bg-danger';

        setTimeout(connectToEventStream, 5000);
    };

    eventSource.onopen = () => {
        document.getElementById('connectionStatus').innerHTML = '<i class="bi bi-broadcast"></i> Connected';
        document.getElementById('connectionStatus').className = 'badge bg-success';
    };
}

function handleExecutionUpdate(execution) {
    loadMonitoringData();
}

document.addEventListener('DOMContentLoaded', () => {
    loadMonitoringData();
    connectToEventStream();

    document.getElementById('btnRefreshStats').addEventListener('click', loadMonitoringData);

    setInterval(loadMonitoringData, 10000);
});

window.addEventListener('beforeunload', () => {
    if (eventSource) {
        eventSource.close();
    }
});
