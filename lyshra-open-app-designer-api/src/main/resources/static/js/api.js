/**
 * API client utilities for Lyshra OpenApp Designer
 */

class ApiClient {
    constructor(baseUrl = '/api/v1') {
        this.baseUrl = baseUrl;
    }

    async request(endpoint, options = {}) {
        const token = getToken();
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };

        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        try {
            const response = await fetch(`${this.baseUrl}${endpoint}`, {
                ...options,
                headers
            });

            if (response.status === 401) {
                clearToken();
                window.location.href = '/login';
                return null;
            }

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || `HTTP ${response.status}`);
            }

            if (response.status === 204) {
                return null;
            }

            return await response.json();
        } catch (error) {
            console.error('API request failed:', error);
            throw error;
        }
    }

    get(endpoint) {
        return this.request(endpoint, { method: 'GET' });
    }

    post(endpoint, data) {
        return this.request(endpoint, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    }

    put(endpoint, data) {
        return this.request(endpoint, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    }

    delete(endpoint) {
        return this.request(endpoint, { method: 'DELETE' });
    }

    // Workflow APIs
    async getWorkflows(params = {}) {
        const queryString = new URLSearchParams(params).toString();
        return this.get(`/workflows${queryString ? '?' + queryString : ''}`);
    }

    async getWorkflow(id) {
        return this.get(`/workflows/${id}`);
    }

    async createWorkflow(data) {
        return this.post('/workflows', data);
    }

    async updateWorkflow(id, data) {
        return this.put(`/workflows/${id}`, data);
    }

    async deleteWorkflow(id) {
        return this.delete(`/workflows/${id}`);
    }

    async duplicateWorkflow(id, newName) {
        return this.post(`/workflows/${id}/duplicate?newName=${encodeURIComponent(newName)}`);
    }

    async changeWorkflowState(id, state) {
        return this.put(`/workflows/${id}/state?state=${state}`);
    }

    // Version APIs
    async getVersions(workflowId) {
        return this.get(`/workflows/${workflowId}/versions`);
    }

    async getVersion(versionId) {
        return this.get(`/workflows/versions/${versionId}`);
    }

    async createVersion(workflowId, data) {
        return this.post(`/workflows/${workflowId}/versions`, data);
    }

    async activateVersion(versionId) {
        return this.put(`/workflows/versions/${versionId}/activate`);
    }

    async deprecateVersion(versionId) {
        return this.put(`/workflows/versions/${versionId}/deprecate`);
    }

    async rollbackToVersion(workflowId, versionId) {
        return this.put(`/workflows/${workflowId}/rollback?targetVersionId=${versionId}`);
    }

    // Execution APIs
    async getExecutions(params = {}) {
        const queryString = new URLSearchParams(params).toString();
        return this.get(`/executions${queryString ? '?' + queryString : ''}`);
    }

    async getExecution(id) {
        return this.get(`/executions/${id}`);
    }

    async startExecution(workflowId, data = {}) {
        return this.post(`/executions/workflows/${workflowId}`, data);
    }

    async cancelExecution(id) {
        return this.put(`/executions/${id}/cancel`);
    }

    async retryExecution(id) {
        return this.post(`/executions/${id}/retry`);
    }

    async getRunningExecutions() {
        return this.get('/executions/running');
    }

    async getStatistics() {
        return this.get('/executions/statistics');
    }

    async getWorkflowStatistics(workflowId) {
        return this.get(`/executions/statistics/workflows/${workflowId}`);
    }

    // Processor APIs
    async getProcessors(params = {}) {
        const queryString = new URLSearchParams(params).toString();
        return this.get(`/processors${queryString ? '?' + queryString : ''}`);
    }

    async getProcessor(identifier) {
        return this.get(`/processors/${encodeURIComponent(identifier)}`);
    }

    async getProcessorCategories() {
        return this.get('/processors/categories');
    }

    // Validation APIs
    async validateWorkflow(versionData) {
        return this.post('/validation/workflow', versionData);
    }
}

const api = new ApiClient();

// Utility functions
function formatDuration(ms) {
    if (!ms) return '-';
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);

    if (hours > 0) {
        return `${hours}h ${minutes % 60}m`;
    } else if (minutes > 0) {
        return `${minutes}m ${seconds % 60}s`;
    } else {
        return `${seconds}s`;
    }
}

function formatDate(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString();
}

function formatRelativeTime(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    const now = new Date();
    const diff = now - date;

    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (minutes > 0) return `${minutes}m ago`;
    return 'just now';
}

function getStatusBadgeClass(status) {
    const statusMap = {
        'PENDING': 'bg-secondary',
        'RUNNING': 'bg-primary',
        'WAITING': 'bg-info',
        'RETRYING': 'bg-warning text-dark',
        'COMPLETED': 'bg-success',
        'FAILED': 'bg-danger',
        'ABORTED': 'bg-danger',
        'CANCELLED': 'bg-warning text-dark',
        'DRAFT': 'bg-secondary',
        'ACTIVE': 'bg-success',
        'DEPRECATED': 'bg-warning text-dark',
        'ARCHIVED': 'bg-dark'
    };
    return statusMap[status] || 'bg-secondary';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
