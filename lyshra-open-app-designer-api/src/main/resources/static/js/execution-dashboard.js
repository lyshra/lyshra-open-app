/**
 * Execution Dashboard for Lyshra OpenApp Designer
 * Comprehensive dashboard for monitoring workflow executions with:
 * - Real-time status updates
 * - Advanced filtering, searching, and sorting
 * - System health indicators
 * - Detailed execution views
 */

class ExecutionDashboard {
    constructor(options = {}) {
        this.executions = [];
        this.filteredExecutions = [];
        this.workflows = [];
        this.statistics = null;
        this.autoRefreshInterval = null;
        this.autoRefreshEnabled = true;
        this.refreshRate = options.refreshRate || 5000;
        this.currentPage = 1;
        this.pageSize = options.pageSize || 20;
        this.sortField = 'startedAt';
        this.sortDirection = 'desc';
        this.filters = {
            search: '',
            status: '',
            workflow: '',
            triggeredBy: '',
            dateFrom: '',
            dateTo: ''
        };
        this.selectedExecution = null;
        this.container = null;
    }

    /**
     * Initialize the dashboard
     */
    async init(container) {
        this.container = container;
        await this.loadWorkflows();
        this.render();
        await this.refresh();

        if (this.autoRefreshEnabled) {
            this.startAutoRefresh();
        }
    }

    /**
     * Load available workflows for filter
     */
    async loadWorkflows() {
        try {
            this.workflows = await api.getWorkflows();
        } catch (error) {
            console.error('Failed to load workflows:', error);
            this.workflows = [];
        }
    }

    /**
     * Refresh dashboard data
     */
    async refresh() {
        try {
            const [executions, stats] = await Promise.all([
                api.getExecutions(),
                api.getStatistics()
            ]);

            this.executions = executions || [];
            this.statistics = stats;
            this.applyFiltersAndSort();
            this.updateUI();
        } catch (error) {
            console.error('Failed to refresh dashboard:', error);
        }
    }

    /**
     * Render the dashboard
     */
    render() {
        if (!this.container) return;

        this.container.innerHTML = `
            <div class="execution-dashboard">
                <!-- System Health Overview -->
                <div class="health-overview mb-4">
                    <div class="row g-3">
                        <div class="col-lg-8">
                            <div class="row g-3">
                                ${this.renderStatCards()}
                            </div>
                        </div>
                        <div class="col-lg-4">
                            <div class="card h-100">
                                <div class="card-body d-flex flex-column justify-content-center">
                                    <div class="health-indicator" id="healthIndicator">
                                        <div class="health-status healthy">
                                            <i class="bi bi-check-circle-fill"></i>
                                        </div>
                                        <div class="health-text">
                                            <strong>System Health</strong>
                                            <span class="text-success">Healthy</span>
                                        </div>
                                    </div>
                                    <div class="health-details mt-3" id="healthDetails">
                                        <div class="d-flex justify-content-between small">
                                            <span>Active Executions</span>
                                            <strong id="activeCount">0</strong>
                                        </div>
                                        <div class="d-flex justify-content-between small">
                                            <span>Avg. Duration (24h)</span>
                                            <strong id="avgDuration">-</strong>
                                        </div>
                                        <div class="d-flex justify-content-between small">
                                            <span>Error Rate (24h)</span>
                                            <strong id="errorRate">0%</strong>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Quick Filters -->
                <div class="quick-filters mb-3">
                    <div class="btn-group" role="group">
                        <button type="button" class="btn btn-outline-secondary quick-filter active" data-filter="all">
                            All
                        </button>
                        <button type="button" class="btn btn-outline-primary quick-filter" data-filter="running">
                            <i class="bi bi-play-circle"></i> Running
                        </button>
                        <button type="button" class="btn btn-outline-danger quick-filter" data-filter="failed">
                            <i class="bi bi-x-circle"></i> Failed
                        </button>
                        <button type="button" class="btn btn-outline-success quick-filter" data-filter="completed">
                            <i class="bi bi-check-circle"></i> Completed
                        </button>
                        <button type="button" class="btn btn-outline-warning quick-filter" data-filter="pending">
                            <i class="bi bi-hourglass"></i> Pending
                        </button>
                    </div>
                    <div class="d-flex align-items-center gap-2 ms-auto">
                        <div class="form-check form-switch mb-0">
                            <input class="form-check-input" type="checkbox" id="dashAutoRefresh"
                                   ${this.autoRefreshEnabled ? 'checked' : ''}>
                            <label class="form-check-label small" for="dashAutoRefresh">Auto-refresh</label>
                        </div>
                        <button class="btn btn-sm btn-outline-secondary" onclick="executionDashboard.refresh()">
                            <i class="bi bi-arrow-clockwise"></i>
                        </button>
                    </div>
                </div>

                <!-- Advanced Filters -->
                <div class="card mb-4">
                    <div class="card-header d-flex justify-content-between align-items-center py-2">
                        <span><i class="bi bi-funnel"></i> Filters</span>
                        <button class="btn btn-sm btn-link" type="button" data-bs-toggle="collapse"
                                data-bs-target="#advancedFilters">
                            <i class="bi bi-chevron-down"></i> Advanced
                        </button>
                    </div>
                    <div class="card-body py-3">
                        <div class="row g-3">
                            <div class="col-md-4">
                                <div class="input-group">
                                    <span class="input-group-text"><i class="bi bi-search"></i></span>
                                    <input type="text" class="form-control" id="searchExecutions"
                                           placeholder="Search by ID, workflow, correlation ID...">
                                </div>
                            </div>
                            <div class="col-md-3">
                                <select class="form-select" id="filterWorkflow">
                                    <option value="">All Workflows</option>
                                    ${this.workflows.map(w => `
                                        <option value="${w.id}">${escapeHtml(w.name)}</option>
                                    `).join('')}
                                </select>
                            </div>
                            <div class="col-md-3">
                                <select class="form-select" id="filterStatus">
                                    <option value="">All Statuses</option>
                                    <option value="PENDING">Pending</option>
                                    <option value="RUNNING">Running</option>
                                    <option value="WAITING">Waiting</option>
                                    <option value="RETRYING">Retrying</option>
                                    <option value="COMPLETED">Completed</option>
                                    <option value="FAILED">Failed</option>
                                    <option value="ABORTED">Aborted</option>
                                    <option value="CANCELLED">Cancelled</option>
                                </select>
                            </div>
                            <div class="col-md-2">
                                <button class="btn btn-outline-secondary w-100" onclick="executionDashboard.clearFilters()">
                                    <i class="bi bi-x-circle"></i> Clear
                                </button>
                            </div>
                        </div>

                        <!-- Advanced Filters (Collapsed) -->
                        <div class="collapse mt-3" id="advancedFilters">
                            <div class="row g-3">
                                <div class="col-md-3">
                                    <label class="form-label small">Triggered By</label>
                                    <input type="text" class="form-control" id="filterTriggeredBy"
                                           placeholder="User or system">
                                </div>
                                <div class="col-md-3">
                                    <label class="form-label small">From Date</label>
                                    <input type="datetime-local" class="form-control" id="filterDateFrom">
                                </div>
                                <div class="col-md-3">
                                    <label class="form-label small">To Date</label>
                                    <input type="datetime-local" class="form-control" id="filterDateTo">
                                </div>
                                <div class="col-md-3">
                                    <label class="form-label small">Sort By</label>
                                    <select class="form-select" id="sortBy">
                                        <option value="startedAt-desc">Started (Newest)</option>
                                        <option value="startedAt-asc">Started (Oldest)</option>
                                        <option value="duration-desc">Duration (Longest)</option>
                                        <option value="duration-asc">Duration (Shortest)</option>
                                        <option value="status-asc">Status (A-Z)</option>
                                        <option value="workflowName-asc">Workflow (A-Z)</option>
                                    </select>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Results Summary -->
                <div class="d-flex justify-content-between align-items-center mb-3">
                    <div class="results-summary" id="resultsSummary">
                        Showing <strong>0</strong> executions
                    </div>
                    <div class="d-flex align-items-center gap-2">
                        <label class="small text-muted mb-0">Page Size:</label>
                        <select class="form-select form-select-sm" id="pageSize" style="width: auto;">
                            <option value="10">10</option>
                            <option value="20" selected>20</option>
                            <option value="50">50</option>
                            <option value="100">100</option>
                        </select>
                    </div>
                </div>

                <!-- Executions Table -->
                <div class="card">
                    <div class="table-responsive">
                        <table class="table table-hover execution-table mb-0">
                            <thead class="table-light">
                                <tr>
                                    <th class="sortable" data-field="id" style="width: 100px;">
                                        ID <i class="bi bi-arrow-down-up"></i>
                                    </th>
                                    <th class="sortable" data-field="workflowName">
                                        Workflow <i class="bi bi-arrow-down-up"></i>
                                    </th>
                                    <th style="width: 100px;">Version</th>
                                    <th class="sortable" data-field="status" style="width: 120px;">
                                        Status <i class="bi bi-arrow-down-up"></i>
                                    </th>
                                    <th class="sortable" data-field="startedAt" style="width: 160px;">
                                        Started <i class="bi bi-arrow-down-up"></i>
                                    </th>
                                    <th class="sortable" data-field="duration" style="width: 100px;">
                                        Duration <i class="bi bi-arrow-down-up"></i>
                                    </th>
                                    <th style="width: 120px;">Owner</th>
                                    <th style="width: 80px;">Progress</th>
                                    <th style="width: 100px;">Actions</th>
                                </tr>
                            </thead>
                            <tbody id="executionTableBody">
                                <tr>
                                    <td colspan="9" class="text-center py-4">
                                        <div class="spinner-border text-primary" role="status"></div>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>

                <!-- Pagination -->
                <nav class="mt-3" id="paginationContainer">
                    <ul class="pagination justify-content-center mb-0" id="pagination">
                    </ul>
                </nav>
            </div>

            ${this.renderExecutionDetailModal()}
        `;

        this.attachEventListeners();
    }

    /**
     * Render stat cards HTML
     */
    renderStatCards() {
        return `
            <div class="col-6 col-md-3">
                <div class="stat-card stat-total">
                    <div class="stat-icon"><i class="bi bi-collection"></i></div>
                    <div class="stat-content">
                        <div class="stat-value" id="statTotal">-</div>
                        <div class="stat-label">Total</div>
                    </div>
                </div>
            </div>
            <div class="col-6 col-md-3">
                <div class="stat-card stat-running">
                    <div class="stat-icon"><i class="bi bi-play-circle"></i></div>
                    <div class="stat-content">
                        <div class="stat-value" id="statRunning">-</div>
                        <div class="stat-label">Running</div>
                    </div>
                </div>
            </div>
            <div class="col-6 col-md-3">
                <div class="stat-card stat-success">
                    <div class="stat-icon"><i class="bi bi-check-circle"></i></div>
                    <div class="stat-content">
                        <div class="stat-value" id="statCompleted">-</div>
                        <div class="stat-label">Completed</div>
                    </div>
                </div>
            </div>
            <div class="col-6 col-md-3">
                <div class="stat-card stat-failed">
                    <div class="stat-icon"><i class="bi bi-x-circle"></i></div>
                    <div class="stat-content">
                        <div class="stat-value" id="statFailed">-</div>
                        <div class="stat-label">Failed</div>
                    </div>
                </div>
            </div>
        `;
    }

    /**
     * Render execution detail modal
     */
    renderExecutionDetailModal() {
        return `
            <div class="modal fade" id="execDetailModal" tabindex="-1">
                <div class="modal-dialog modal-xl">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title">
                                <i class="bi bi-info-circle"></i> Execution Details
                                <span class="badge ms-2" id="modalStatusBadge"></span>
                            </h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                        </div>
                        <div class="modal-body" id="execDetailContent">
                            <!-- Content loaded dynamically -->
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-warning" id="btnModalCancel" style="display: none;">
                                <i class="bi bi-stop-circle"></i> Cancel Execution
                            </button>
                            <button type="button" class="btn btn-primary" id="btnModalRetry" style="display: none;">
                                <i class="bi bi-arrow-repeat"></i> Retry
                            </button>
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
        // Search
        const searchInput = document.getElementById('searchExecutions');
        searchInput?.addEventListener('input', this.debounce(() => {
            this.filters.search = searchInput.value;
            this.currentPage = 1;
            this.applyFiltersAndSort();
            this.updateUI();
        }, 300));

        // Filter dropdowns
        ['filterStatus', 'filterWorkflow'].forEach(id => {
            const el = document.getElementById(id);
            el?.addEventListener('change', () => {
                this.filters[id.replace('filter', '').toLowerCase()] = el.value;
                this.currentPage = 1;
                this.applyFiltersAndSort();
                this.updateUI();
            });
        });

        // Advanced filters
        ['filterTriggeredBy', 'filterDateFrom', 'filterDateTo'].forEach(id => {
            const el = document.getElementById(id);
            el?.addEventListener('change', () => {
                const key = id.replace('filter', '');
                this.filters[key.charAt(0).toLowerCase() + key.slice(1)] = el.value;
                this.currentPage = 1;
                this.applyFiltersAndSort();
                this.updateUI();
            });
        });

        // Sort dropdown
        document.getElementById('sortBy')?.addEventListener('change', (e) => {
            const [field, direction] = e.target.value.split('-');
            this.sortField = field;
            this.sortDirection = direction;
            this.applyFiltersAndSort();
            this.updateUI();
        });

        // Quick filters
        document.querySelectorAll('.quick-filter').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.quick-filter').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                const filter = btn.dataset.filter;
                this.applyQuickFilter(filter);
            });
        });

        // Auto refresh toggle
        document.getElementById('dashAutoRefresh')?.addEventListener('change', (e) => {
            this.autoRefreshEnabled = e.target.checked;
            if (this.autoRefreshEnabled) {
                this.startAutoRefresh();
            } else {
                this.stopAutoRefresh();
            }
        });

        // Page size
        document.getElementById('pageSize')?.addEventListener('change', (e) => {
            this.pageSize = parseInt(e.target.value);
            this.currentPage = 1;
            this.updateUI();
        });

        // Sortable headers
        document.querySelectorAll('.sortable').forEach(th => {
            th.addEventListener('click', () => {
                const field = th.dataset.field;
                if (this.sortField === field) {
                    this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
                } else {
                    this.sortField = field;
                    this.sortDirection = 'desc';
                }
                this.applyFiltersAndSort();
                this.updateUI();
            });
        });

        // Modal actions
        document.getElementById('btnModalCancel')?.addEventListener('click', () => {
            if (this.selectedExecution) this.cancelExecution(this.selectedExecution.id);
        });

        document.getElementById('btnModalRetry')?.addEventListener('click', () => {
            if (this.selectedExecution) this.retryExecution(this.selectedExecution.id);
        });
    }

    /**
     * Apply quick filter
     */
    applyQuickFilter(filter) {
        const statusSelect = document.getElementById('filterStatus');
        switch (filter) {
            case 'running':
                this.filters.status = 'RUNNING';
                if (statusSelect) statusSelect.value = 'RUNNING';
                break;
            case 'failed':
                this.filters.status = 'FAILED';
                if (statusSelect) statusSelect.value = 'FAILED';
                break;
            case 'completed':
                this.filters.status = 'COMPLETED';
                if (statusSelect) statusSelect.value = 'COMPLETED';
                break;
            case 'pending':
                this.filters.status = 'PENDING';
                if (statusSelect) statusSelect.value = 'PENDING';
                break;
            default:
                this.filters.status = '';
                if (statusSelect) statusSelect.value = '';
        }
        this.currentPage = 1;
        this.applyFiltersAndSort();
        this.updateUI();
    }

    /**
     * Clear all filters
     */
    clearFilters() {
        this.filters = {
            search: '',
            status: '',
            workflow: '',
            triggeredBy: '',
            dateFrom: '',
            dateTo: ''
        };

        // Reset form inputs
        document.getElementById('searchExecutions').value = '';
        document.getElementById('filterStatus').value = '';
        document.getElementById('filterWorkflow').value = '';
        document.getElementById('filterTriggeredBy').value = '';
        document.getElementById('filterDateFrom').value = '';
        document.getElementById('filterDateTo').value = '';

        // Reset quick filters
        document.querySelectorAll('.quick-filter').forEach(b => b.classList.remove('active'));
        document.querySelector('.quick-filter[data-filter="all"]')?.classList.add('active');

        this.currentPage = 1;
        this.applyFiltersAndSort();
        this.updateUI();
    }

    /**
     * Apply filters and sorting
     */
    applyFiltersAndSort() {
        let filtered = [...this.executions];

        // Apply search filter
        if (this.filters.search) {
            const search = this.filters.search.toLowerCase();
            filtered = filtered.filter(exec =>
                exec.id.toLowerCase().includes(search) ||
                exec.workflowName?.toLowerCase().includes(search) ||
                exec.correlationId?.toLowerCase().includes(search) ||
                exec.triggeredBy?.toLowerCase().includes(search)
            );
        }

        // Apply status filter
        if (this.filters.status) {
            filtered = filtered.filter(exec => exec.status === this.filters.status);
        }

        // Apply workflow filter
        if (this.filters.workflow) {
            filtered = filtered.filter(exec => exec.workflowDefinitionId === this.filters.workflow);
        }

        // Apply triggered by filter
        if (this.filters.triggeredBy) {
            const trigger = this.filters.triggeredBy.toLowerCase();
            filtered = filtered.filter(exec =>
                exec.triggeredBy?.toLowerCase().includes(trigger)
            );
        }

        // Apply date filters
        if (this.filters.dateFrom) {
            const fromDate = new Date(this.filters.dateFrom);
            filtered = filtered.filter(exec =>
                new Date(exec.startedAt) >= fromDate
            );
        }

        if (this.filters.dateTo) {
            const toDate = new Date(this.filters.dateTo);
            filtered = filtered.filter(exec =>
                new Date(exec.startedAt) <= toDate
            );
        }

        // Apply sorting
        filtered.sort((a, b) => {
            let valueA, valueB;

            switch (this.sortField) {
                case 'id':
                    valueA = a.id;
                    valueB = b.id;
                    break;
                case 'workflowName':
                    valueA = a.workflowName || '';
                    valueB = b.workflowName || '';
                    break;
                case 'status':
                    valueA = a.status;
                    valueB = b.status;
                    break;
                case 'startedAt':
                    valueA = new Date(a.startedAt);
                    valueB = new Date(b.startedAt);
                    break;
                case 'duration':
                    valueA = this.getDuration(a);
                    valueB = this.getDuration(b);
                    break;
                default:
                    valueA = a[this.sortField];
                    valueB = b[this.sortField];
            }

            if (valueA < valueB) return this.sortDirection === 'asc' ? -1 : 1;
            if (valueA > valueB) return this.sortDirection === 'asc' ? 1 : -1;
            return 0;
        });

        this.filteredExecutions = filtered;
    }

    /**
     * Update UI components
     */
    updateUI() {
        this.updateStatistics();
        this.updateHealthIndicator();
        this.updateTable();
        this.updatePagination();
        this.updateResultsSummary();
    }

    /**
     * Update statistics cards
     */
    updateStatistics() {
        if (!this.statistics) return;

        document.getElementById('statTotal').textContent = this.statistics.totalExecutions || 0;
        document.getElementById('statRunning').textContent = this.statistics.runningExecutions || 0;
        document.getElementById('statCompleted').textContent = this.statistics.completedExecutions || 0;
        document.getElementById('statFailed').textContent = this.statistics.failedExecutions || 0;
    }

    /**
     * Update health indicator
     */
    updateHealthIndicator() {
        const indicator = document.getElementById('healthIndicator');
        if (!indicator || !this.statistics) return;

        const failureRate = this.statistics.totalExecutions > 0
            ? (this.statistics.failedExecutions / this.statistics.totalExecutions) * 100
            : 0;

        const runningCount = this.statistics.runningExecutions || 0;

        let status, statusClass, statusText;

        if (failureRate > 20 || runningCount > 50) {
            status = 'critical';
            statusClass = 'text-danger';
            statusText = 'Critical';
        } else if (failureRate > 10 || runningCount > 30) {
            status = 'warning';
            statusClass = 'text-warning';
            statusText = 'Warning';
        } else {
            status = 'healthy';
            statusClass = 'text-success';
            statusText = 'Healthy';
        }

        indicator.innerHTML = `
            <div class="health-status ${status}">
                <i class="bi bi-${status === 'healthy' ? 'check' : status === 'warning' ? 'exclamation' : 'x'}-circle-fill"></i>
            </div>
            <div class="health-text">
                <strong>System Health</strong>
                <span class="${statusClass}">${statusText}</span>
            </div>
        `;

        // Update details
        document.getElementById('activeCount').textContent = runningCount;
        document.getElementById('avgDuration').textContent =
            this.statistics.averageDuration ? formatDuration(this.statistics.averageDuration) : '-';
        document.getElementById('errorRate').textContent = failureRate.toFixed(1) + '%';
    }

    /**
     * Update table
     */
    updateTable() {
        const tbody = document.getElementById('executionTableBody');
        if (!tbody) return;

        const startIdx = (this.currentPage - 1) * this.pageSize;
        const pageData = this.filteredExecutions.slice(startIdx, startIdx + this.pageSize);

        if (pageData.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="9" class="text-center py-5">
                        <i class="bi bi-inbox display-4 text-muted"></i>
                        <p class="mt-3 text-muted mb-0">No executions found</p>
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = pageData.map(exec => this.renderExecutionRow(exec)).join('');
    }

    /**
     * Render a single execution row
     */
    renderExecutionRow(exec) {
        const duration = this.getDuration(exec);
        const isRunning = ['RUNNING', 'WAITING', 'RETRYING'].includes(exec.status);
        const isFailed = exec.status === 'FAILED';

        return `
            <tr class="execution-row ${isFailed ? 'row-failed' : ''}" data-id="${exec.id}">
                <td>
                    <a href="#" onclick="executionDashboard.showDetails('${exec.id}'); return false;"
                       class="text-decoration-none">
                        <code class="small">${exec.id.substring(0, 8)}...</code>
                    </a>
                    ${exec.correlationId ? `
                        <div class="small text-muted" title="Correlation ID">
                            <i class="bi bi-link-45deg"></i> ${exec.correlationId.substring(0, 12)}...
                        </div>
                    ` : ''}
                </td>
                <td>
                    <strong>${escapeHtml(exec.workflowName || 'Unknown')}</strong>
                    ${exec.currentStepName && isRunning ? `
                        <div class="small text-muted">
                            <i class="bi bi-arrow-right"></i> ${escapeHtml(exec.currentStepName)}
                        </div>
                    ` : ''}
                </td>
                <td>
                    <span class="badge bg-secondary">${exec.versionNumber || '-'}</span>
                </td>
                <td>
                    <span class="badge ${getStatusBadgeClass(exec.status)}">${exec.status}</span>
                    ${exec.errorMessage ? `
                        <i class="bi bi-exclamation-circle text-danger ms-1" title="${escapeHtml(exec.errorMessage)}"></i>
                    ` : ''}
                </td>
                <td>
                    <div>${formatDate(exec.startedAt)}</div>
                    <div class="small text-muted">${formatRelativeTime(exec.startedAt)}</div>
                </td>
                <td>
                    ${duration > 0 ? formatDuration(duration) : '-'}
                    ${isRunning ? '<span class="running-indicator"></span>' : ''}
                </td>
                <td>
                    <div class="d-flex align-items-center gap-2">
                        <div class="owner-avatar">
                            ${(exec.triggeredBy || 'S')[0].toUpperCase()}
                        </div>
                        <span class="small">${exec.triggeredBy || 'System'}</span>
                    </div>
                </td>
                <td>
                    ${this.renderProgressIndicator(exec)}
                </td>
                <td>
                    <div class="btn-group btn-group-sm">
                        <button class="btn btn-outline-secondary" onclick="executionDashboard.showDetails('${exec.id}')"
                                title="View Details">
                            <i class="bi bi-eye"></i>
                        </button>
                        ${isRunning ? `
                            <button class="btn btn-outline-warning" onclick="executionDashboard.cancelExecution('${exec.id}')"
                                    title="Cancel">
                                <i class="bi bi-stop-circle"></i>
                            </button>
                        ` : ''}
                        ${isFailed ? `
                            <button class="btn btn-outline-primary" onclick="executionDashboard.retryExecution('${exec.id}')"
                                    title="Retry">
                                <i class="bi bi-arrow-repeat"></i>
                            </button>
                        ` : ''}
                    </div>
                </td>
            </tr>
        `;
    }

    /**
     * Render progress indicator
     */
    renderProgressIndicator(exec) {
        const stepLogs = exec.stepLogs || [];
        const completedSteps = stepLogs.filter(s => s.status === 'COMPLETED').length;
        const totalSteps = stepLogs.length || 1;
        const progress = Math.round((completedSteps / totalSteps) * 100);

        if (['COMPLETED', 'FAILED', 'ABORTED', 'CANCELLED'].includes(exec.status)) {
            return `
                <div class="progress" style="height: 6px;">
                    <div class="progress-bar ${exec.status === 'COMPLETED' ? 'bg-success' : 'bg-danger'}"
                         style="width: 100%"></div>
                </div>
            `;
        }

        return `
            <div class="progress" style="height: 6px;">
                <div class="progress-bar progress-bar-striped progress-bar-animated"
                     style="width: ${progress}%"></div>
            </div>
            <div class="small text-muted text-center">${completedSteps}/${totalSteps}</div>
        `;
    }

    /**
     * Update pagination
     */
    updatePagination() {
        const container = document.getElementById('pagination');
        if (!container) return;

        const totalPages = Math.ceil(this.filteredExecutions.length / this.pageSize);

        if (totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        let html = '';

        // Previous button
        html += `
            <li class="page-item ${this.currentPage === 1 ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="executionDashboard.goToPage(${this.currentPage - 1}); return false;">
                    <i class="bi bi-chevron-left"></i>
                </a>
            </li>
        `;

        // Page numbers
        const maxVisible = 5;
        let startPage = Math.max(1, this.currentPage - Math.floor(maxVisible / 2));
        let endPage = Math.min(totalPages, startPage + maxVisible - 1);

        if (endPage - startPage < maxVisible - 1) {
            startPage = Math.max(1, endPage - maxVisible + 1);
        }

        if (startPage > 1) {
            html += `<li class="page-item"><a class="page-link" href="#" onclick="executionDashboard.goToPage(1); return false;">1</a></li>`;
            if (startPage > 2) {
                html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
        }

        for (let i = startPage; i <= endPage; i++) {
            html += `
                <li class="page-item ${i === this.currentPage ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="executionDashboard.goToPage(${i}); return false;">${i}</a>
                </li>
            `;
        }

        if (endPage < totalPages) {
            if (endPage < totalPages - 1) {
                html += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
            html += `<li class="page-item"><a class="page-link" href="#" onclick="executionDashboard.goToPage(${totalPages}); return false;">${totalPages}</a></li>`;
        }

        // Next button
        html += `
            <li class="page-item ${this.currentPage === totalPages ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="executionDashboard.goToPage(${this.currentPage + 1}); return false;">
                    <i class="bi bi-chevron-right"></i>
                </a>
            </li>
        `;

        container.innerHTML = html;
    }

    /**
     * Update results summary
     */
    updateResultsSummary() {
        const summary = document.getElementById('resultsSummary');
        if (!summary) return;

        const total = this.filteredExecutions.length;
        const startIdx = (this.currentPage - 1) * this.pageSize + 1;
        const endIdx = Math.min(this.currentPage * this.pageSize, total);

        if (total === 0) {
            summary.innerHTML = 'No executions found';
        } else {
            summary.innerHTML = `Showing <strong>${startIdx}-${endIdx}</strong> of <strong>${total}</strong> executions`;
        }
    }

    /**
     * Go to page
     */
    goToPage(page) {
        const totalPages = Math.ceil(this.filteredExecutions.length / this.pageSize);
        this.currentPage = Math.max(1, Math.min(page, totalPages));
        this.updateTable();
        this.updatePagination();
        this.updateResultsSummary();
    }

    /**
     * Show execution details
     */
    async showDetails(executionId) {
        try {
            const exec = await api.getExecution(executionId);
            this.selectedExecution = exec;

            const content = document.getElementById('execDetailContent');
            const statusBadge = document.getElementById('modalStatusBadge');

            statusBadge.className = `badge ${getStatusBadgeClass(exec.status)}`;
            statusBadge.textContent = exec.status;

            content.innerHTML = `
                <div class="row">
                    <div class="col-md-6">
                        <div class="card mb-3">
                            <div class="card-header">General Information</div>
                            <div class="card-body">
                                <dl class="row mb-0">
                                    <dt class="col-sm-4">Execution ID</dt>
                                    <dd class="col-sm-8"><code>${exec.id}</code></dd>

                                    <dt class="col-sm-4">Workflow</dt>
                                    <dd class="col-sm-8">${escapeHtml(exec.workflowName || 'Unknown')}</dd>

                                    <dt class="col-sm-4">Version</dt>
                                    <dd class="col-sm-8"><span class="badge bg-secondary">${exec.versionNumber || '-'}</span></dd>

                                    <dt class="col-sm-4">Triggered By</dt>
                                    <dd class="col-sm-8">${exec.triggeredBy || 'System'}</dd>

                                    <dt class="col-sm-4">Correlation ID</dt>
                                    <dd class="col-sm-8"><code>${exec.correlationId || '-'}</code></dd>
                                </dl>
                            </div>
                        </div>
                    </div>
                    <div class="col-md-6">
                        <div class="card mb-3">
                            <div class="card-header">Timing</div>
                            <div class="card-body">
                                <dl class="row mb-0">
                                    <dt class="col-sm-4">Started</dt>
                                    <dd class="col-sm-8">${formatDate(exec.startedAt)}</dd>

                                    <dt class="col-sm-4">Completed</dt>
                                    <dd class="col-sm-8">${exec.completedAt ? formatDate(exec.completedAt) : '-'}</dd>

                                    <dt class="col-sm-4">Duration</dt>
                                    <dd class="col-sm-8">${this.getDuration(exec) > 0 ? formatDuration(this.getDuration(exec)) : '-'}</dd>

                                    <dt class="col-sm-4">Current Step</dt>
                                    <dd class="col-sm-8">${exec.currentStepName || '-'}</dd>
                                </dl>
                            </div>
                        </div>
                    </div>
                </div>

                ${exec.errorMessage ? `
                    <div class="alert alert-danger">
                        <h6 class="alert-heading"><i class="bi bi-exclamation-triangle"></i> Error</h6>
                        <p class="mb-1"><strong>${exec.errorCode || 'ERROR'}</strong></p>
                        <p class="mb-0">${escapeHtml(exec.errorMessage)}</p>
                    </div>
                ` : ''}

                <ul class="nav nav-tabs" role="tablist">
                    <li class="nav-item">
                        <a class="nav-link active" data-bs-toggle="tab" href="#stepsTab">
                            <i class="bi bi-list-ol"></i> Steps (${exec.stepLogs?.length || 0})
                        </a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" data-bs-toggle="tab" href="#inputTab">
                            <i class="bi bi-box-arrow-in-right"></i> Input
                        </a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" data-bs-toggle="tab" href="#outputTab">
                            <i class="bi bi-box-arrow-right"></i> Output
                        </a>
                    </li>
                    <li class="nav-item">
                        <a class="nav-link" data-bs-toggle="tab" href="#variablesTab">
                            <i class="bi bi-collection"></i> Variables
                        </a>
                    </li>
                </ul>

                <div class="tab-content pt-3">
                    <div class="tab-pane fade show active" id="stepsTab">
                        ${this.renderStepLogs(exec.stepLogs)}
                    </div>
                    <div class="tab-pane fade" id="inputTab">
                        <pre class="code-block">${escapeHtml(JSON.stringify(exec.inputData || {}, null, 2))}</pre>
                    </div>
                    <div class="tab-pane fade" id="outputTab">
                        <pre class="code-block">${escapeHtml(JSON.stringify(exec.outputData || {}, null, 2))}</pre>
                    </div>
                    <div class="tab-pane fade" id="variablesTab">
                        <pre class="code-block">${escapeHtml(JSON.stringify(exec.variables || {}, null, 2))}</pre>
                    </div>
                </div>
            `;

            // Update action buttons
            const cancelBtn = document.getElementById('btnModalCancel');
            const retryBtn = document.getElementById('btnModalRetry');

            const isRunning = ['RUNNING', 'WAITING', 'PENDING', 'RETRYING'].includes(exec.status);
            cancelBtn.style.display = isRunning ? '' : 'none';
            retryBtn.style.display = exec.status === 'FAILED' ? '' : 'none';

            new bootstrap.Modal(document.getElementById('execDetailModal')).show();
        } catch (error) {
            console.error('Failed to load execution details:', error);
            alert('Failed to load execution details: ' + error.message);
        }
    }

    /**
     * Render step logs
     */
    renderStepLogs(stepLogs) {
        if (!stepLogs || stepLogs.length === 0) {
            return '<p class="text-muted">No step logs available</p>';
        }

        return `
            <div class="step-timeline">
                ${stepLogs.map((log, idx) => `
                    <div class="step-item ${log.status === 'FAILED' ? 'step-failed' : log.status === 'COMPLETED' ? 'step-completed' : 'step-running'}">
                        <div class="step-marker">
                            <i class="bi bi-${log.status === 'COMPLETED' ? 'check' : log.status === 'FAILED' ? 'x' : 'play'}"></i>
                        </div>
                        <div class="step-content">
                            <div class="d-flex justify-content-between align-items-start">
                                <div>
                                    <strong>${escapeHtml(log.stepName)}</strong>
                                    <span class="badge ${getStatusBadgeClass(log.status)} ms-2">${log.status}</span>
                                </div>
                                <span class="text-muted small">
                                    ${log.completedAt ? formatDuration(new Date(log.completedAt) - new Date(log.startedAt)) : '-'}
                                </span>
                            </div>
                            <div class="small text-muted">
                                Started: ${formatDate(log.startedAt)}
                                ${log.branch ? ` | Branch: ${log.branch}` : ''}
                            </div>
                            ${log.errorMessage ? `
                                <div class="alert alert-danger alert-sm mt-2 mb-0 py-1 px-2">
                                    <small>${escapeHtml(log.errorMessage)}</small>
                                </div>
                            ` : ''}
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    /**
     * Cancel execution
     */
    async cancelExecution(executionId) {
        if (!confirm('Cancel this execution?')) return;

        try {
            await api.cancelExecution(executionId);
            this.showToast('Execution cancelled', 'warning');
            await this.refresh();

            if (this.selectedExecution?.id === executionId) {
                this.showDetails(executionId);
            }
        } catch (error) {
            alert('Failed to cancel execution: ' + error.message);
        }
    }

    /**
     * Retry execution
     */
    async retryExecution(executionId) {
        if (!confirm('Retry this execution?')) return;

        try {
            const newExec = await api.retryExecution(executionId);
            this.showToast('Execution retried successfully', 'success');
            await this.refresh();

            bootstrap.Modal.getInstance(document.getElementById('execDetailModal'))?.hide();
            this.showDetails(newExec.id);
        } catch (error) {
            alert('Failed to retry execution: ' + error.message);
        }
    }

    /**
     * Get execution duration in ms
     */
    getDuration(exec) {
        if (!exec.startedAt) return 0;
        const end = exec.completedAt ? new Date(exec.completedAt) : new Date();
        return end - new Date(exec.startedAt);
    }

    /**
     * Start auto refresh
     */
    startAutoRefresh() {
        this.stopAutoRefresh();
        this.autoRefreshInterval = setInterval(() => this.refresh(), this.refreshRate);
    }

    /**
     * Stop auto refresh
     */
    stopAutoRefresh() {
        if (this.autoRefreshInterval) {
            clearInterval(this.autoRefreshInterval);
            this.autoRefreshInterval = null;
        }
    }

    /**
     * Debounce utility
     */
    debounce(func, wait) {
        let timeout;
        return (...args) => {
            clearTimeout(timeout);
            timeout = setTimeout(() => func.apply(this, args), wait);
        };
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
                    ${message}
                    <button type="button" class="btn-close btn-close-white ms-auto" data-bs-dismiss="toast"></button>
                </div>
            </div>
        `;

        const toastEl = document.getElementById(toastId);
        new bootstrap.Toast(toastEl, { delay: 4000 }).show();
        toastEl.addEventListener('hidden.bs.toast', () => toastEl.remove());
    }

    /**
     * Destroy dashboard
     */
    destroy() {
        this.stopAutoRefresh();
    }
}

// Global instance
let executionDashboard;
