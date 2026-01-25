/**
 * Workflow Designer for Lyshra OpenApp
 * Provides drag-and-drop workflow design capabilities with:
 * - Canvas zoom and pan
 * - Grid snapping
 * - Interactive node connections
 * - Minimap overview
 */

class WorkflowDesigner {
    constructor() {
        // DOM Elements
        this.canvas = document.getElementById('workflowCanvas');
        this.stepsLayer = document.getElementById('stepsLayer');
        this.connectionsLayer = document.getElementById('connectionsLayer');
        this.canvasContainer = document.getElementById('canvasContainer');

        // Workflow data
        this.workflow = null;
        this.version = null;
        this.steps = new Map();
        this.connections = new Map();
        this.processors = [];

        // Selection state
        this.selectedStep = null;
        this.selectedConnection = null;
        this.draggedProcessor = null;

        // Canvas state
        this.zoom = 1;
        this.panX = 0;
        this.panY = 0;
        this.isPanning = false;
        this.panStartX = 0;
        this.panStartY = 0;

        // Grid settings
        this.gridSize = 20;
        this.snapToGrid = true;

        // Zoom limits
        this.minZoom = 0.25;
        this.maxZoom = 3;
        this.zoomStep = 0.1;

        // Connection drawing state
        this.isConnecting = false;
        this.connectionStartStep = null;
        this.connectionStartPort = null;
        this.tempConnectionLine = null;

        // Step dragging state
        this.isDraggingStep = false;
        this.draggedStep = null;
        this.dragOffsetX = 0;
        this.dragOffsetY = 0;

        // Undo/Redo history
        this.history = [];
        this.historyIndex = -1;
        this.maxHistory = 50;

        // Canvas dimensions
        this.canvasWidth = 4000;
        this.canvasHeight = 3000;

        this.init();
    }

    async init() {
        await this.loadProcessors();
        this.setupEventListeners();
        this.setupDragAndDrop();
        this.setupCanvasInteractions();
        this.setupKeyboardShortcuts();
        this.updateZoomIndicator();
        this.updateMinimap();
    }

    // ============================================
    // Processor Loading & Palette
    // ============================================

    async loadProcessors() {
        try {
            this.processors = await api.getProcessors();
            this.renderProcessorPalette();
        } catch (error) {
            console.error('Failed to load processors:', error);
            // Load mock processors for development
            this.processors = this.getMockProcessors();
            this.renderProcessorPalette();
        }
    }

    getMockProcessors() {
        return [
            { identifier: 'set-variable', displayName: 'Set Variable', category: 'Control Flow', icon: 'box-arrow-right', description: 'Set a workflow variable', processorName: 'SetVariableProcessor' },
            { identifier: 'if-condition', displayName: 'If Condition', category: 'Control Flow', icon: 'signpost-split', description: 'Conditional branching', processorName: 'IfConditionProcessor' },
            { identifier: 'switch', displayName: 'Switch', category: 'Control Flow', icon: 'diagram-3', description: 'Multi-way branching', processorName: 'SwitchProcessor' },
            { identifier: 'foreach', displayName: 'ForEach Loop', category: 'Control Flow', icon: 'arrow-repeat', description: 'Iterate over a collection', processorName: 'ForEachProcessor' },
            { identifier: 'javascript', displayName: 'JavaScript', category: 'Scripting', icon: 'code-slash', description: 'Execute JavaScript code', processorName: 'JavaScriptProcessor' },
            { identifier: 'groovy', displayName: 'Groovy Script', category: 'Scripting', icon: 'terminal', description: 'Execute Groovy code', processorName: 'GroovyProcessor' },
            { identifier: 'http-request', displayName: 'HTTP Request', category: 'Integration', icon: 'globe', description: 'Make HTTP/REST calls', processorName: 'HttpRequestProcessor' },
            { identifier: 'email-send', displayName: 'Send Email', category: 'Integration', icon: 'envelope', description: 'Send email notifications', processorName: 'EmailSendProcessor' },
            { identifier: 'json-transform', displayName: 'JSON Transform', category: 'Data Operations', icon: 'braces', description: 'Transform JSON data', processorName: 'JsonTransformProcessor' },
            { identifier: 'data-mapper', displayName: 'Data Mapper', category: 'Data Operations', icon: 'arrow-left-right', description: 'Map data between formats', processorName: 'DataMapperProcessor' },
            { identifier: 'mongodb-query', displayName: 'MongoDB Query', category: 'Database', icon: 'database', description: 'Query MongoDB database', processorName: 'MongoDbQueryProcessor' },
            { identifier: 'sql-query', displayName: 'SQL Query', category: 'Database', icon: 'table', description: 'Execute SQL queries', processorName: 'SqlQueryProcessor' },
        ];
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
            const categoryId = category.replace(/\s/g, '');
            html += `
                <div class="processor-category">
                    <div class="processor-category-header" data-bs-toggle="collapse"
                         data-bs-target="#cat-${categoryId}" aria-expanded="true">
                        <i class="bi bi-chevron-down me-1"></i>${category}
                        <span class="badge bg-secondary ms-auto">${procs.length}</span>
                    </div>
                    <div class="collapse show" id="cat-${categoryId}">
            `;
            procs.forEach(proc => {
                html += `
                    <div class="processor-item d-flex align-items-start"
                         draggable="true"
                         data-processor-id="${proc.identifier}">
                        <div class="processor-icon">
                            <i class="bi bi-${proc.icon || 'box'}"></i>
                        </div>
                        <div class="flex-grow-1">
                            <div class="processor-name">${proc.displayName}</div>
                            <div class="processor-desc">${proc.description || ''}</div>
                        </div>
                    </div>
                `;
            });
            html += '</div></div>';
        }

        palette.innerHTML = html;

        // Setup drag events for processor items
        palette.querySelectorAll('.processor-item').forEach(item => {
            item.addEventListener('dragstart', (e) => {
                this.draggedProcessor = this.processors.find(
                    p => p.identifier === item.dataset.processorId
                );
                e.dataTransfer.setData('text/plain', item.dataset.processorId);
                e.dataTransfer.effectAllowed = 'copy';
                item.classList.add('dragging');
            });

            item.addEventListener('dragend', () => {
                this.draggedProcessor = null;
                item.classList.remove('dragging');
            });
        });

        // Setup search
        const searchInput = document.getElementById('processorSearch');
        searchInput.addEventListener('input', (e) => {
            const query = e.target.value.toLowerCase();
            palette.querySelectorAll('.processor-item').forEach(item => {
                const name = item.querySelector('.processor-name').textContent.toLowerCase();
                const desc = item.querySelector('.processor-desc').textContent.toLowerCase();
                item.style.display = (name.includes(query) || desc.includes(query)) ? '' : 'none';
            });
            // Show all categories when searching
            palette.querySelectorAll('.collapse').forEach(collapse => {
                if (query) {
                    collapse.classList.add('show');
                }
            });
        });
    }

    // ============================================
    // Event Listeners
    // ============================================

    setupEventListeners() {
        // Toolbar buttons
        document.getElementById('btnNew').addEventListener('click', () => this.newWorkflow());
        document.getElementById('btnSave').addEventListener('click', () => this.saveWorkflow());
        document.getElementById('btnValidate').addEventListener('click', () => this.validateWorkflow());
        document.getElementById('btnExecute').addEventListener('click', () => this.executeWorkflow());
        document.getElementById('btnZoomIn').addEventListener('click', () => this.zoomIn());
        document.getElementById('btnZoomOut').addEventListener('click', () => this.zoomOut());
        document.getElementById('btnFitView').addEventListener('click', () => this.fitView());
        document.getElementById('btnSaveWorkflow').addEventListener('click', () => this.saveWorkflowProperties());
        document.getElementById('btnSaveStep').addEventListener('click', () => this.saveStepProperties());

        // Undo/Redo buttons
        const btnUndo = document.getElementById('btnUndo');
        const btnRedo = document.getElementById('btnRedo');
        if (btnUndo) btnUndo.addEventListener('click', () => this.undo());
        if (btnRedo) btnRedo.addEventListener('click', () => this.redo());

        // Grid snap toggle
        const btnSnapGrid = document.getElementById('btnSnapGrid');
        if (btnSnapGrid) {
            btnSnapGrid.addEventListener('click', () => this.toggleSnapToGrid());
        }

        // Canvas zoom buttons
        const btnZoomInCanvas = document.getElementById('btnZoomInCanvas');
        const btnZoomOutCanvas = document.getElementById('btnZoomOutCanvas');
        if (btnZoomInCanvas) btnZoomInCanvas.addEventListener('click', () => this.zoomIn());
        if (btnZoomOutCanvas) btnZoomOutCanvas.addEventListener('click', () => this.zoomOut());

        // Prevent context menu on canvas
        this.canvas.addEventListener('contextmenu', (e) => e.preventDefault());

        // Click on empty canvas to deselect
        this.canvas.addEventListener('click', (e) => {
            if (e.target === this.canvas ||
                (e.target.tagName === 'rect' && e.target.getAttribute('fill') === 'url(#grid)')) {
                this.deselectAll();
            }
        });

        // Context menu items
        document.getElementById('ctxEditStep').addEventListener('click', () => this.editSelectedStep());
        document.getElementById('ctxDeleteStep').addEventListener('click', () => this.deleteSelectedStep());
        document.getElementById('ctxSetStartStep').addEventListener('click', () => this.setStartStep());
        document.getElementById('ctxDuplicateStep').addEventListener('click', () => this.duplicateSelectedStep());

        const ctxConnect = document.getElementById('ctxConnectFrom');
        if (ctxConnect) {
            ctxConnect.addEventListener('click', () => this.startConnectionFromSelected());
        }

        // Close context menu on click outside
        document.addEventListener('click', (e) => {
            if (!e.target.closest('#contextMenu')) {
                this.hideContextMenu();
            }
        });

        // Window resize
        window.addEventListener('resize', () => {
            this.updateMinimap();
        });
    }

    setupDragAndDrop() {
        this.canvasContainer.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
        });

        this.canvasContainer.addEventListener('drop', (e) => {
            e.preventDefault();
            if (this.draggedProcessor) {
                const point = this.screenToCanvas(e.clientX, e.clientY);
                const snappedX = this.snapToGrid ? this.snapValue(point.x) : point.x;
                const snappedY = this.snapToGrid ? this.snapValue(point.y) : point.y;
                this.addStep(this.draggedProcessor, snappedX, snappedY);
            }
        });
    }

    setupCanvasInteractions() {
        // Mouse wheel zoom
        this.canvasContainer.addEventListener('wheel', (e) => {
            e.preventDefault();
            const delta = e.deltaY > 0 ? -this.zoomStep : this.zoomStep;
            const mousePoint = this.screenToCanvas(e.clientX, e.clientY);
            this.zoomAtPoint(delta, mousePoint.x, mousePoint.y, e.clientX, e.clientY);
        }, { passive: false });

        // Pan with middle mouse button or spacebar+drag
        this.canvasContainer.addEventListener('mousedown', (e) => {
            // Middle mouse button or left button with space
            if (e.button === 1 || (e.button === 0 && this.spacePressed)) {
                e.preventDefault();
                this.startPan(e.clientX, e.clientY);
            }
        });

        document.addEventListener('mousemove', (e) => {
            if (this.isPanning) {
                this.doPan(e.clientX, e.clientY);
            }
            if (this.isDraggingStep && this.draggedStep) {
                this.doStepDrag(e.clientX, e.clientY);
            }
            if (this.isConnecting) {
                this.updateTempConnection(e.clientX, e.clientY);
            }
        });

        document.addEventListener('mouseup', (e) => {
            if (this.isPanning) {
                this.endPan();
            }
            if (this.isDraggingStep) {
                this.endStepDrag();
            }
            if (this.isConnecting) {
                this.endConnection(e);
            }
        });

        // Touch support for pan
        this.canvasContainer.addEventListener('touchstart', (e) => {
            if (e.touches.length === 2) {
                // Two finger pan
                const touch = e.touches[0];
                this.startPan(touch.clientX, touch.clientY);
            }
        }, { passive: false });

        this.canvasContainer.addEventListener('touchmove', (e) => {
            if (this.isPanning && e.touches.length === 2) {
                e.preventDefault();
                const touch = e.touches[0];
                this.doPan(touch.clientX, touch.clientY);
            }
        }, { passive: false });

        this.canvasContainer.addEventListener('touchend', () => {
            if (this.isPanning) {
                this.endPan();
            }
        });
    }

    setupKeyboardShortcuts() {
        this.spacePressed = false;

        document.addEventListener('keydown', (e) => {
            // Ignore if typing in an input
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
                return;
            }

            switch (e.key) {
                case ' ':
                    e.preventDefault();
                    this.spacePressed = true;
                    this.canvasContainer.style.cursor = 'grab';
                    break;
                case 'Delete':
                case 'Backspace':
                    if (this.selectedStep) {
                        this.deleteStep(this.selectedStep);
                    } else if (this.selectedConnection) {
                        this.deleteConnection(this.selectedConnection);
                    }
                    break;
                case 'Escape':
                    this.cancelConnection();
                    this.deselectAll();
                    break;
                case 'z':
                    if (e.ctrlKey || e.metaKey) {
                        e.preventDefault();
                        if (e.shiftKey) {
                            this.redo();
                        } else {
                            this.undo();
                        }
                    }
                    break;
                case 'y':
                    if (e.ctrlKey || e.metaKey) {
                        e.preventDefault();
                        this.redo();
                    }
                    break;
                case 's':
                    if (e.ctrlKey || e.metaKey) {
                        e.preventDefault();
                        this.saveWorkflow();
                    }
                    break;
                case '+':
                case '=':
                    if (e.ctrlKey || e.metaKey) {
                        e.preventDefault();
                        this.zoomIn();
                    }
                    break;
                case '-':
                    if (e.ctrlKey || e.metaKey) {
                        e.preventDefault();
                        this.zoomOut();
                    }
                    break;
                case '0':
                    if (e.ctrlKey || e.metaKey) {
                        e.preventDefault();
                        this.fitView();
                    }
                    break;
            }
        });

        document.addEventListener('keyup', (e) => {
            if (e.key === ' ') {
                this.spacePressed = false;
                this.canvasContainer.style.cursor = '';
            }
        });
    }

    // ============================================
    // Coordinate Transformations
    // ============================================

    screenToCanvas(screenX, screenY) {
        const rect = this.canvasContainer.getBoundingClientRect();
        return {
            x: (screenX - rect.left - this.panX) / this.zoom,
            y: (screenY - rect.top - this.panY) / this.zoom
        };
    }

    canvasToScreen(canvasX, canvasY) {
        const rect = this.canvasContainer.getBoundingClientRect();
        return {
            x: canvasX * this.zoom + this.panX + rect.left,
            y: canvasY * this.zoom + this.panY + rect.top
        };
    }

    snapValue(value) {
        return Math.round(value / this.gridSize) * this.gridSize;
    }

    // ============================================
    // Zoom & Pan
    // ============================================

    zoomIn() {
        const newZoom = Math.min(this.zoom + this.zoomStep, this.maxZoom);
        this.setZoom(newZoom);
    }

    zoomOut() {
        const newZoom = Math.max(this.zoom - this.zoomStep, this.minZoom);
        this.setZoom(newZoom);
    }

    zoomAtPoint(delta, canvasX, canvasY, screenX, screenY) {
        const oldZoom = this.zoom;
        const newZoom = Math.min(Math.max(this.zoom + delta, this.minZoom), this.maxZoom);

        if (newZoom !== oldZoom) {
            // Adjust pan to zoom toward mouse position
            const rect = this.canvasContainer.getBoundingClientRect();
            const mouseX = screenX - rect.left;
            const mouseY = screenY - rect.top;

            this.panX = mouseX - (mouseX - this.panX) * (newZoom / oldZoom);
            this.panY = mouseY - (mouseY - this.panY) * (newZoom / oldZoom);
            this.zoom = newZoom;

            this.applyTransform();
            this.updateZoomIndicator();
            this.updateMinimap();
        }
    }

    setZoom(newZoom) {
        // Zoom centered on viewport
        const rect = this.canvasContainer.getBoundingClientRect();
        const centerX = rect.width / 2;
        const centerY = rect.height / 2;

        const oldZoom = this.zoom;
        this.panX = centerX - (centerX - this.panX) * (newZoom / oldZoom);
        this.panY = centerY - (centerY - this.panY) * (newZoom / oldZoom);
        this.zoom = newZoom;

        this.applyTransform();
        this.updateZoomIndicator();
        this.updateMinimap();
    }

    fitView() {
        if (this.steps.size === 0) {
            this.zoom = 1;
            this.panX = 0;
            this.panY = 0;
        } else {
            // Calculate bounding box of all steps
            let minX = Infinity, minY = Infinity;
            let maxX = -Infinity, maxY = -Infinity;

            this.steps.forEach(step => {
                minX = Math.min(minX, step.position.x);
                minY = Math.min(minY, step.position.y);
                maxX = Math.max(maxX, step.position.x + step.position.width);
                maxY = Math.max(maxY, step.position.y + step.position.height);
            });

            const padding = 50;
            const rect = this.canvasContainer.getBoundingClientRect();
            const contentWidth = maxX - minX + padding * 2;
            const contentHeight = maxY - minY + padding * 2;

            const scaleX = rect.width / contentWidth;
            const scaleY = rect.height / contentHeight;
            this.zoom = Math.min(Math.max(Math.min(scaleX, scaleY), this.minZoom), this.maxZoom);

            // Center the content
            const centerX = (minX + maxX) / 2;
            const centerY = (minY + maxY) / 2;
            this.panX = rect.width / 2 - centerX * this.zoom;
            this.panY = rect.height / 2 - centerY * this.zoom;
        }

        this.applyTransform();
        this.updateZoomIndicator();
        this.updateMinimap();
    }

    startPan(x, y) {
        this.isPanning = true;
        this.panStartX = x - this.panX;
        this.panStartY = y - this.panY;
        this.canvasContainer.style.cursor = 'grabbing';
    }

    doPan(x, y) {
        this.panX = x - this.panStartX;
        this.panY = y - this.panStartY;
        this.applyTransform();
        this.updateMinimap();
    }

    endPan() {
        this.isPanning = false;
        this.canvasContainer.style.cursor = '';
    }

    applyTransform() {
        const transform = `translate(${this.panX}, ${this.panY}) scale(${this.zoom})`;
        this.stepsLayer.setAttribute('transform', transform);
        this.connectionsLayer.setAttribute('transform', transform);

        // Update grid pattern scale
        const gridPattern = this.canvas.querySelector('#grid');
        if (gridPattern) {
            const scaledSize = this.gridSize * this.zoom;
            gridPattern.setAttribute('width', scaledSize);
            gridPattern.setAttribute('height', scaledSize);
            gridPattern.setAttribute('patternTransform', `translate(${this.panX}, ${this.panY})`);
        }
    }

    updateZoomIndicator() {
        const indicator = document.getElementById('zoomIndicator');
        if (indicator) {
            indicator.textContent = `${Math.round(this.zoom * 100)}%`;
        }
    }

    toggleSnapToGrid() {
        this.snapToGrid = !this.snapToGrid;
        const btn = document.getElementById('btnSnapGrid');
        if (btn) {
            btn.classList.toggle('active', this.snapToGrid);
        }
    }

    // ============================================
    // Minimap
    // ============================================

    updateMinimap() {
        const minimap = document.getElementById('minimap');
        if (!minimap) return;

        const minimapCanvas = minimap.querySelector('canvas');
        if (!minimapCanvas) return;

        const ctx = minimapCanvas.getContext('2d');
        const scale = minimap.offsetWidth / this.canvasWidth;

        minimapCanvas.width = minimap.offsetWidth;
        minimapCanvas.height = minimap.offsetHeight;

        // Clear
        ctx.fillStyle = '#f8f9fa';
        ctx.fillRect(0, 0, minimapCanvas.width, minimapCanvas.height);

        // Draw steps
        ctx.fillStyle = '#0d6efd';
        this.steps.forEach(step => {
            ctx.fillRect(
                step.position.x * scale,
                step.position.y * scale,
                step.position.width * scale,
                step.position.height * scale
            );
        });

        // Draw viewport rectangle
        const rect = this.canvasContainer.getBoundingClientRect();
        const viewX = (-this.panX / this.zoom) * scale;
        const viewY = (-this.panY / this.zoom) * scale;
        const viewW = (rect.width / this.zoom) * scale;
        const viewH = (rect.height / this.zoom) * scale;

        ctx.strokeStyle = '#dc3545';
        ctx.lineWidth = 2;
        ctx.strokeRect(viewX, viewY, viewW, viewH);
    }

    // ============================================
    // Step Management
    // ============================================

    addStep(processor, x, y) {
        const stepId = 'step-' + Date.now() + '-' + Math.random().toString(36).substr(2, 9);
        const step = {
            id: stepId,
            name: processor.displayName,
            type: 'PROCESSOR',
            processor: {
                organization: processor.pluginOrganization || 'lyshra',
                module: processor.pluginModule || 'core',
                version: processor.pluginVersion || '1.0.0',
                processorName: processor.processorName
            },
            inputConfig: {},
            position: { x, y, width: 180, height: 70 }
        };

        this.steps.set(stepId, step);
        this.renderStep(step);
        this.saveToHistory('add_step', { step: { ...step } });

        if (this.steps.size === 1) {
            if (!this.version) {
                this.version = { startStepId: stepId, steps: [], connections: {} };
            } else {
                this.version.startStepId = stepId;
            }
            this.markAsStartStep(stepId);
        }

        this.updateMinimap();
        return step;
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

        // Get processor icon
        const processor = this.processors.find(p => p.processorName === step.processor?.processorName);
        const icon = processor?.icon || 'box';

        g.innerHTML = `
            <!-- Main rectangle -->
            <rect class="step-body" width="${step.position.width}" height="${step.position.height}" rx="8"></rect>

            <!-- Header area -->
            <rect class="step-header" x="0" y="0" width="${step.position.width}" height="24" rx="8" ry="8"></rect>
            <rect class="step-header-bottom" x="0" y="16" width="${step.position.width}" height="8"></rect>

            <!-- Icon and name -->
            <text x="28" y="16" class="step-icon-text">
                <tspan class="bi">${this.getIconChar(icon)}</tspan>
            </text>
            <text x="40" y="16" class="step-name" font-size="11" font-weight="600">${escapeHtml(step.name)}</text>

            <!-- Processor type -->
            <text x="${step.position.width / 2}" y="45" text-anchor="middle" class="step-type" font-size="10">
                ${step.processor ? step.processor.processorName : 'Workflow'}
            </text>

            <!-- Start indicator -->
            ${isStart ? '<circle class="start-indicator" cx="10" cy="10" r="5"/>' : ''}

            <!-- Connection ports -->
            <g class="connection-ports">
                <!-- Input port (left) -->
                <circle class="port port-input" cx="0" cy="${step.position.height / 2}" r="6" data-port="input"/>

                <!-- Output port (right) -->
                <circle class="port port-output" cx="${step.position.width}" cy="${step.position.height / 2}" r="6" data-port="output"/>

                <!-- Error port (bottom) -->
                <circle class="port port-error" cx="${step.position.width / 2}" cy="${step.position.height}" r="5" data-port="error"/>
            </g>
        `;

        this.setupStepInteractions(g, step);
        this.stepsLayer.appendChild(g);
    }

    getIconChar(iconName) {
        // Bootstrap Icons mapping for SVG text
        const iconMap = {
            'box': '\uF1C7',
            'box-arrow-right': '\uF1C5',
            'signpost-split': '\uF5D3',
            'diagram-3': '\uF2A2',
            'arrow-repeat': '\uF129',
            'code-slash': '\uF1F5',
            'terminal': '\uF68A',
            'globe': '\uF3E5',
            'envelope': '\uF32C',
            'braces': '\uF1BB',
            'arrow-left-right': '\uF11B',
            'database': '\uF29F',
            'table': '\uF637'
        };
        return iconMap[iconName] || '\uF1C7';
    }

    setupStepInteractions(g, step) {
        // Mouse down on step body - start drag
        g.querySelector('.step-body').addEventListener('mousedown', (e) => {
            if (e.button === 0 && !this.spacePressed) {
                e.stopPropagation();
                this.startStepDrag(step.id, e.clientX, e.clientY);
            }
        });

        // Click to select
        g.addEventListener('click', (e) => {
            e.stopPropagation();
            if (!this.isDraggingStep || !this.hasDragged) {
                this.selectStep(step.id);
            }
        });

        // Double-click to edit
        g.addEventListener('dblclick', (e) => {
            e.stopPropagation();
            this.editStep(step.id);
        });

        // Right-click context menu
        g.addEventListener('contextmenu', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.selectStep(step.id);
            this.showContextMenu(e.clientX, e.clientY);
        });

        // Port interactions for connections
        g.querySelectorAll('.port').forEach(port => {
            port.addEventListener('mousedown', (e) => {
                e.stopPropagation();
                const portType = port.dataset.port;
                if (portType === 'output' || portType === 'error') {
                    this.startConnection(step.id, portType, e.clientX, e.clientY);
                }
            });

            port.addEventListener('mouseup', (e) => {
                if (this.isConnecting && port.dataset.port === 'input') {
                    this.completeConnection(step.id);
                }
            });

            port.addEventListener('mouseenter', () => {
                if (this.isConnecting && port.dataset.port === 'input') {
                    port.classList.add('port-highlight');
                }
            });

            port.addEventListener('mouseleave', () => {
                port.classList.remove('port-highlight');
            });
        });
    }

    startStepDrag(stepId, clientX, clientY) {
        const step = this.steps.get(stepId);
        if (!step) return;

        this.isDraggingStep = true;
        this.draggedStep = stepId;
        this.hasDragged = false;

        const point = this.screenToCanvas(clientX, clientY);
        this.dragOffsetX = point.x - step.position.x;
        this.dragOffsetY = point.y - step.position.y;

        // Store original position for undo
        this.dragStartPosition = { x: step.position.x, y: step.position.y };

        this.selectStep(stepId);
    }

    doStepDrag(clientX, clientY) {
        const step = this.steps.get(this.draggedStep);
        if (!step) return;

        this.hasDragged = true;

        const point = this.screenToCanvas(clientX, clientY);
        let newX = point.x - this.dragOffsetX;
        let newY = point.y - this.dragOffsetY;

        // Apply grid snapping
        if (this.snapToGrid) {
            newX = this.snapValue(newX);
            newY = this.snapValue(newY);
        }

        // Constrain to canvas bounds
        newX = Math.max(0, Math.min(newX, this.canvasWidth - step.position.width));
        newY = Math.max(0, Math.min(newY, this.canvasHeight - step.position.height));

        step.position.x = newX;
        step.position.y = newY;

        // Update visual position
        const g = this.stepsLayer.querySelector(`[data-step-id="${this.draggedStep}"]`);
        if (g) {
            g.setAttribute('transform', `translate(${newX}, ${newY})`);
        }

        // Update connections
        this.updateConnectionsForStep(this.draggedStep);
    }

    endStepDrag() {
        if (this.hasDragged && this.draggedStep && this.dragStartPosition) {
            const step = this.steps.get(this.draggedStep);
            if (step && (step.position.x !== this.dragStartPosition.x || step.position.y !== this.dragStartPosition.y)) {
                this.saveToHistory('move_step', {
                    stepId: this.draggedStep,
                    oldPosition: this.dragStartPosition,
                    newPosition: { x: step.position.x, y: step.position.y }
                });
            }
        }

        this.isDraggingStep = false;
        this.draggedStep = null;
        this.hasDragged = false;
        this.dragStartPosition = null;
        this.updateMinimap();
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
        const processor = this.processors.find(p => p.processorName === step.processor?.processorName);

        panel.innerHTML = `
            <div class="property-group">
                <div class="property-group-header">
                    <i class="bi bi-info-circle"></i> Step Properties
                </div>
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
                <div class="property-group-header">
                    <i class="bi bi-geo"></i> Position
                </div>
                <div class="row g-2">
                    <div class="col-6">
                        <label class="form-label small">X</label>
                        <input type="number" class="form-control form-control-sm" id="propPosX"
                               value="${Math.round(step.position.x)}">
                    </div>
                    <div class="col-6">
                        <label class="form-label small">Y</label>
                        <input type="number" class="form-control form-control-sm" id="propPosY"
                               value="${Math.round(step.position.y)}">
                    </div>
                </div>
            </div>
            <div class="property-group">
                <div class="property-group-header">
                    <i class="bi bi-sliders"></i> Configuration
                </div>
                <button class="btn btn-outline-primary btn-sm w-100" onclick="designer.editStep('${stepId}')">
                    <i class="bi bi-pencil"></i> Edit Configuration
                </button>
            </div>
            <div class="property-group">
                <div class="property-group-header">
                    <i class="bi bi-link-45deg"></i> Connections
                </div>
                <div id="stepConnections" class="small">
                    ${this.renderStepConnections(stepId)}
                </div>
            </div>
            <div class="property-group">
                <div class="property-group-header">
                    <i class="bi bi-gear"></i> Actions
                </div>
                <div class="d-grid gap-2">
                    <button class="btn btn-outline-success btn-sm" onclick="designer.setAsStartStep('${stepId}')">
                        <i class="bi bi-flag"></i> Set as Start
                    </button>
                    <button class="btn btn-outline-secondary btn-sm" onclick="designer.duplicateStep('${stepId}')">
                        <i class="bi bi-copy"></i> Duplicate
                    </button>
                    <button class="btn btn-outline-danger btn-sm" onclick="designer.deleteStep('${stepId}')">
                        <i class="bi bi-trash"></i> Delete
                    </button>
                </div>
            </div>
        `;

        // Add event listeners for property changes
        document.getElementById('propStepName').addEventListener('change', (e) => {
            step.name = e.target.value;
            const g = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);
            if (g) {
                g.querySelector('.step-name').textContent = step.name;
            }
        });

        document.getElementById('propPosX').addEventListener('change', (e) => {
            const newX = parseInt(e.target.value) || 0;
            this.moveStep(stepId, newX, step.position.y);
        });

        document.getElementById('propPosY').addEventListener('change', (e) => {
            const newY = parseInt(e.target.value) || 0;
            this.moveStep(stepId, step.position.x, newY);
        });
    }

    renderStepConnections(stepId) {
        const incoming = [];
        const outgoing = [];

        this.connections.forEach((conn, connId) => {
            if (conn.targetStepId === stepId) {
                const source = this.steps.get(conn.sourceStepId);
                incoming.push({ conn, source });
            }
            if (conn.sourceStepId === stepId) {
                const target = this.steps.get(conn.targetStepId);
                outgoing.push({ conn, target });
            }
        });

        let html = '';
        if (incoming.length > 0) {
            html += '<div class="mb-2"><strong>Incoming:</strong></div>';
            incoming.forEach(({ conn, source }) => {
                html += `<div class="ps-2 mb-1">← ${source ? escapeHtml(source.name) : 'Unknown'}</div>`;
            });
        }
        if (outgoing.length > 0) {
            html += '<div class="mb-2"><strong>Outgoing:</strong></div>';
            outgoing.forEach(({ conn, target }) => {
                const type = conn.type === 'error' ? '(error)' : '';
                html += `<div class="ps-2 mb-1">→ ${target ? escapeHtml(target.name) : 'Unknown'} ${type}</div>`;
            });
        }
        if (incoming.length === 0 && outgoing.length === 0) {
            html = '<div class="text-muted">No connections</div>';
        }

        return html;
    }

    moveStep(stepId, x, y) {
        const step = this.steps.get(stepId);
        if (!step) return;

        const oldPos = { x: step.position.x, y: step.position.y };
        step.position.x = this.snapToGrid ? this.snapValue(x) : x;
        step.position.y = this.snapToGrid ? this.snapValue(y) : y;

        const g = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);
        if (g) {
            g.setAttribute('transform', `translate(${step.position.x}, ${step.position.y})`);
        }

        this.updateConnectionsForStep(stepId);
        this.saveToHistory('move_step', {
            stepId,
            oldPosition: oldPos,
            newPosition: { x: step.position.x, y: step.position.y }
        });
        this.updateMinimap();
    }

    deleteStep(stepId) {
        if (!confirm('Are you sure you want to delete this step?')) return;

        const step = this.steps.get(stepId);
        if (!step) return;

        // Remove connected connections
        const removedConnections = [];
        this.connections.forEach((conn, connId) => {
            if (conn.sourceStepId === stepId || conn.targetStepId === stepId) {
                removedConnections.push({ ...conn });
                this.connections.delete(connId);
                const line = this.connectionsLayer.querySelector(`[data-connection-id="${connId}"]`);
                if (line) line.remove();
            }
        });

        // Remove step
        this.steps.delete(stepId);
        const g = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);
        if (g) g.remove();

        // Update start step if needed
        if (this.version && this.version.startStepId === stepId) {
            this.version.startStepId = null;
        }

        this.saveToHistory('delete_step', { step: { ...step }, connections: removedConnections });
        this.deselectAll();
        this.updateMinimap();
    }

    duplicateStep(stepId) {
        const original = this.steps.get(stepId);
        if (!original) return;

        const processor = this.processors.find(p =>
            p.processorName === original.processor?.processorName
        );
        if (processor) {
            const newStep = this.addStep(
                processor,
                original.position.x + 40,
                original.position.y + 40
            );
            // Copy configuration
            newStep.inputConfig = { ...original.inputConfig };
        }
    }

    setAsStartStep(stepId) {
        if (!this.version) {
            this.version = { startStepId: stepId, steps: [], connections: {} };
        }

        const oldStartId = this.version.startStepId;
        this.version.startStepId = stepId;

        // Remove old start indicator
        this.stepsLayer.querySelectorAll('.start-step').forEach(g => {
            g.classList.remove('start-step');
            const circle = g.querySelector('.start-indicator');
            if (circle) circle.remove();
        });

        // Add new start indicator
        this.markAsStartStep(stepId);
        this.saveToHistory('set_start', { oldStartId, newStartId: stepId });
    }

    markAsStartStep(stepId) {
        const g = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);
        if (g) {
            g.classList.add('start-step');
            if (!g.querySelector('.start-indicator')) {
                const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
                circle.setAttribute('class', 'start-indicator');
                circle.setAttribute('cx', '10');
                circle.setAttribute('cy', '10');
                circle.setAttribute('r', '5');
                g.appendChild(circle);
            }
        }
    }

    // ============================================
    // Connection Management
    // ============================================

    startConnection(stepId, portType, clientX, clientY) {
        this.isConnecting = true;
        this.connectionStartStep = stepId;
        this.connectionStartPort = portType;

        // Create temporary connection line
        this.tempConnectionLine = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        this.tempConnectionLine.setAttribute('class', 'connection-line temp-connection');
        if (portType === 'error') {
            this.tempConnectionLine.classList.add('error-connection');
        }
        this.connectionsLayer.appendChild(this.tempConnectionLine);

        this.updateTempConnection(clientX, clientY);
        this.canvasContainer.style.cursor = 'crosshair';
    }

    updateTempConnection(clientX, clientY) {
        if (!this.tempConnectionLine || !this.connectionStartStep) return;

        const step = this.steps.get(this.connectionStartStep);
        if (!step) return;

        // Calculate start point
        let startX, startY;
        if (this.connectionStartPort === 'output') {
            startX = step.position.x + step.position.width;
            startY = step.position.y + step.position.height / 2;
        } else { // error port
            startX = step.position.x + step.position.width / 2;
            startY = step.position.y + step.position.height;
        }

        // Calculate end point (mouse position)
        const endPoint = this.screenToCanvas(clientX, clientY);

        // Draw bezier curve
        const path = this.calculateConnectionPath(startX, startY, endPoint.x, endPoint.y);
        this.tempConnectionLine.setAttribute('d', path);
    }

    completeConnection(targetStepId) {
        if (!this.isConnecting || !this.connectionStartStep) return;

        // Don't connect to self
        if (this.connectionStartStep === targetStepId) {
            this.cancelConnection();
            return;
        }

        // Check if connection already exists
        let exists = false;
        this.connections.forEach(conn => {
            if (conn.sourceStepId === this.connectionStartStep &&
                conn.targetStepId === targetStepId &&
                conn.type === this.connectionStartPort) {
                exists = true;
            }
        });

        if (!exists) {
            this.addConnection(
                this.connectionStartStep,
                targetStepId,
                this.connectionStartPort
            );
        }

        this.cancelConnection();
    }

    endConnection(e) {
        if (!this.isConnecting) return;

        // Check if we're over a step's input port
        const target = e.target;
        if (target.classList.contains('port-input')) {
            const stepG = target.closest('.workflow-step');
            if (stepG) {
                const targetStepId = stepG.dataset.stepId;
                this.completeConnection(targetStepId);
                return;
            }
        }

        this.cancelConnection();
    }

    cancelConnection() {
        if (this.tempConnectionLine) {
            this.tempConnectionLine.remove();
            this.tempConnectionLine = null;
        }
        this.isConnecting = false;
        this.connectionStartStep = null;
        this.connectionStartPort = null;
        this.canvasContainer.style.cursor = '';
    }

    addConnection(sourceStepId, targetStepId, type = 'output') {
        const connId = `conn-${sourceStepId}-${targetStepId}-${type}`;
        const connection = {
            id: connId,
            sourceStepId,
            targetStepId,
            type,
            label: type === 'error' ? 'On Error' : null
        };

        this.connections.set(connId, connection);
        this.renderConnection(connection);
        this.saveToHistory('add_connection', { connection: { ...connection } });

        // Update properties panel if step is selected
        if (this.selectedStep === sourceStepId || this.selectedStep === targetStepId) {
            this.showStepProperties(this.selectedStep);
        }
    }

    renderConnection(conn) {
        let line = this.connectionsLayer.querySelector(`[data-connection-id="${conn.id}"]`);
        if (!line) {
            const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            g.setAttribute('class', 'connection-group');
            g.setAttribute('data-connection-id', conn.id);

            line = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            line.setAttribute('class', 'connection-line');
            if (conn.type === 'error') {
                line.classList.add('error-connection');
            }

            // Click area (wider invisible path for easier selection)
            const hitArea = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            hitArea.setAttribute('class', 'connection-hit-area');

            g.appendChild(hitArea);
            g.appendChild(line);

            // Add click handler
            g.addEventListener('click', (e) => {
                e.stopPropagation();
                this.selectConnection(conn.id);
            });

            g.addEventListener('contextmenu', (e) => {
                e.preventDefault();
                e.stopPropagation();
                this.selectConnection(conn.id);
                this.showConnectionContextMenu(e.clientX, e.clientY, conn.id);
            });

            this.connectionsLayer.appendChild(g);
        }

        const source = this.steps.get(conn.sourceStepId);
        const target = this.steps.get(conn.targetStepId);

        if (source && target) {
            let x1, y1;
            if (conn.type === 'output') {
                x1 = source.position.x + source.position.width;
                y1 = source.position.y + source.position.height / 2;
            } else { // error
                x1 = source.position.x + source.position.width / 2;
                y1 = source.position.y + source.position.height;
            }

            const x2 = target.position.x;
            const y2 = target.position.y + target.position.height / 2;

            const path = this.calculateConnectionPath(x1, y1, x2, y2);

            const g = this.connectionsLayer.querySelector(`[data-connection-id="${conn.id}"]`);
            g.querySelector('.connection-line').setAttribute('d', path);
            g.querySelector('.connection-hit-area').setAttribute('d', path);
        }
    }

    calculateConnectionPath(x1, y1, x2, y2) {
        const dx = Math.abs(x2 - x1);
        const dy = Math.abs(y2 - y1);
        const controlOffset = Math.max(50, Math.min(dx / 2, 150));

        // Bezier curve control points
        const cx1 = x1 + controlOffset;
        const cy1 = y1;
        const cx2 = x2 - controlOffset;
        const cy2 = y2;

        return `M ${x1} ${y1} C ${cx1} ${cy1}, ${cx2} ${cy2}, ${x2} ${y2}`;
    }

    updateConnectionsForStep(stepId) {
        this.connections.forEach((conn, connId) => {
            if (conn.sourceStepId === stepId || conn.targetStepId === stepId) {
                this.renderConnection(conn);
            }
        });
    }

    selectConnection(connId) {
        this.deselectAll();
        this.selectedConnection = connId;
        const g = this.connectionsLayer.querySelector(`[data-connection-id="${connId}"]`);
        if (g) {
            g.classList.add('selected');
        }
    }

    deleteConnection(connId) {
        const conn = this.connections.get(connId);
        if (!conn) return;

        this.connections.delete(connId);
        const g = this.connectionsLayer.querySelector(`[data-connection-id="${connId}"]`);
        if (g) g.remove();

        this.saveToHistory('delete_connection', { connection: { ...conn } });
        this.deselectAll();
    }

    showConnectionContextMenu(x, y, connId) {
        // Create a simple context menu for connections
        const menu = document.getElementById('contextMenu');
        menu.innerHTML = `
            <a class="dropdown-item text-danger" href="#" onclick="designer.deleteConnection('${connId}'); designer.hideContextMenu();">
                <i class="bi bi-trash"></i> Delete Connection
            </a>
        `;
        menu.style.display = 'block';
        menu.style.left = x + 'px';
        menu.style.top = y + 'px';
    }

    startConnectionFromSelected() {
        if (this.selectedStep) {
            const step = this.steps.get(this.selectedStep);
            if (step) {
                const screenPos = this.canvasToScreen(
                    step.position.x + step.position.width,
                    step.position.y + step.position.height / 2
                );
                this.startConnection(this.selectedStep, 'output', screenPos.x, screenPos.y);
            }
        }
        this.hideContextMenu();
    }

    // ============================================
    // Context Menu
    // ============================================

    showContextMenu(x, y) {
        const menu = document.getElementById('contextMenu');
        menu.innerHTML = `
            <a class="dropdown-item" href="#" id="ctxEditStep">
                <i class="bi bi-pencil"></i> Edit Step
            </a>
            <a class="dropdown-item" href="#" id="ctxConnectFrom">
                <i class="bi bi-link-45deg"></i> Connect From Here
            </a>
            <div class="dropdown-divider"></div>
            <a class="dropdown-item" href="#" id="ctxSetStartStep">
                <i class="bi bi-flag"></i> Set as Start Step
            </a>
            <a class="dropdown-item" href="#" id="ctxDuplicateStep">
                <i class="bi bi-copy"></i> Duplicate
            </a>
            <div class="dropdown-divider"></div>
            <a class="dropdown-item text-danger" href="#" id="ctxDeleteStep">
                <i class="bi bi-trash"></i> Delete Step
            </a>
        `;

        // Re-attach event listeners
        document.getElementById('ctxEditStep').addEventListener('click', () => this.editSelectedStep());
        document.getElementById('ctxConnectFrom').addEventListener('click', () => this.startConnectionFromSelected());
        document.getElementById('ctxSetStartStep').addEventListener('click', () => this.setStartStep());
        document.getElementById('ctxDuplicateStep').addEventListener('click', () => this.duplicateSelectedStep());
        document.getElementById('ctxDeleteStep').addEventListener('click', () => this.deleteSelectedStep());

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
            this.duplicateStep(this.selectedStep);
        }
        this.hideContextMenu();
    }

    // ============================================
    // Step Configuration Modal with FormBuilder
    // ============================================

    editStep(stepId) {
        const step = this.steps.get(stepId);
        if (!step) return;

        const modal = document.getElementById('stepModal');
        const body = document.getElementById('stepModalBody');

        // Find the processor definition
        const processor = this.processors.find(p =>
            p.processorName === step.processor?.processorName
        );

        // Create processor config with fields
        const processorConfig = {
            identifier: processor?.identifier || step.processor?.processorName,
            displayName: processor?.displayName || step.name,
            inputFields: processor?.inputFields || this.getDefaultProcessorFields(step.processor?.processorName)
        };

        // Build the form container
        body.innerHTML = `
            <div class="mb-3">
                <label class="form-label">Step Name *</label>
                <input type="text" class="form-control" id="stepName" value="${escapeHtml(step.name)}" required>
                <div class="invalid-feedback">Step name is required</div>
            </div>
            <hr>
            <h6 class="text-muted mb-3">
                <i class="bi bi-gear"></i> Processor Configuration
            </h6>
            <div id="processorConfigForm"></div>
        `;

        // Initialize FormBuilder for processor fields
        const formContainer = document.getElementById('processorConfigForm');
        this.currentFormBuilder = new FormBuilder({
            onChange: (fieldName, value) => {
                this.onStepFieldChange(stepId, fieldName, value);
            },
            onValidate: (fieldName, isValid, message) => {
                this.onStepFieldValidate(stepId, fieldName, isValid, message);
            }
        });

        this.currentFormBuilder.build(formContainer, processorConfig, step.inputConfig || {});
        this.currentEditingStep = stepId;

        // Add step name validation
        const stepNameInput = document.getElementById('stepName');
        stepNameInput.addEventListener('input', () => {
            stepNameInput.classList.remove('is-invalid');
        });

        new bootstrap.Modal(modal).show();
    }

    getDefaultProcessorFields(processorName) {
        // Default field definitions for common processors
        const defaults = {
            'SetVariableProcessor': [
                { name: 'variableName', displayName: 'Variable Name', type: 'STRING', required: true, description: 'Name of the variable to set' },
                { name: 'value', displayName: 'Value', type: 'EXPRESSION', required: true, description: 'Value or expression to assign' }
            ],
            'IfConditionProcessor': [
                { name: 'condition', displayName: 'Condition', type: 'EXPRESSION', required: true, description: 'Boolean expression to evaluate' }
            ],
            'SwitchProcessor': [
                { name: 'expression', displayName: 'Switch Expression', type: 'EXPRESSION', required: true, description: 'Expression to evaluate for cases' },
                { name: 'cases', displayName: 'Cases', type: 'JSON', required: true, description: 'JSON object mapping values to step IDs' }
            ],
            'ForEachProcessor': [
                { name: 'collection', displayName: 'Collection', type: 'EXPRESSION', required: true, description: 'Collection to iterate over' },
                { name: 'itemVariable', displayName: 'Item Variable', type: 'STRING', required: true, description: 'Variable name for current item' },
                { name: 'indexVariable', displayName: 'Index Variable', type: 'STRING', required: false, description: 'Variable name for current index' }
            ],
            'HttpRequestProcessor': [
                { name: 'url', displayName: 'URL', type: 'URL', required: true, description: 'Request URL' },
                { name: 'method', displayName: 'HTTP Method', type: 'SELECT', required: true, options: [
                    { value: 'GET', label: 'GET' }, { value: 'POST', label: 'POST' },
                    { value: 'PUT', label: 'PUT' }, { value: 'DELETE', label: 'DELETE' },
                    { value: 'PATCH', label: 'PATCH' }
                ]},
                { name: 'headers', displayName: 'Headers', type: 'KEY_VALUE_PAIRS', required: false, description: 'Request headers' },
                { name: 'body', displayName: 'Request Body', type: 'JSON', required: false, description: 'Request body (for POST/PUT/PATCH)' },
                { name: 'timeout', displayName: 'Timeout (ms)', type: 'NUMBER', required: false, defaultValue: 30000 }
            ],
            'JavaScriptProcessor': [
                { name: 'script', displayName: 'JavaScript Code', type: 'CODE', required: true, language: 'javascript', description: 'JavaScript code to execute' }
            ],
            'SQLProcessor': [
                { name: 'dataSource', displayName: 'Data Source', type: 'STRING', required: true, description: 'Database connection name' },
                { name: 'query', displayName: 'SQL Query', type: 'CODE', required: true, language: 'sql', description: 'SQL query to execute' },
                { name: 'parameters', displayName: 'Parameters', type: 'JSON', required: false, description: 'Query parameters' }
            ],
            'MongoDBProcessor': [
                { name: 'connectionName', displayName: 'Connection Name', type: 'STRING', required: true },
                { name: 'database', displayName: 'Database', type: 'STRING', required: true },
                { name: 'collection', displayName: 'Collection', type: 'STRING', required: true },
                { name: 'operation', displayName: 'Operation', type: 'SELECT', required: true, options: [
                    { value: 'find', label: 'Find' }, { value: 'findOne', label: 'Find One' },
                    { value: 'insertOne', label: 'Insert One' }, { value: 'insertMany', label: 'Insert Many' },
                    { value: 'updateOne', label: 'Update One' }, { value: 'updateMany', label: 'Update Many' },
                    { value: 'deleteOne', label: 'Delete One' }, { value: 'deleteMany', label: 'Delete Many' },
                    { value: 'aggregate', label: 'Aggregate' }
                ]},
                { name: 'query', displayName: 'Query/Filter', type: 'JSON', required: false },
                { name: 'document', displayName: 'Document', type: 'JSON', required: false }
            ],
            'EmailProcessor': [
                { name: 'to', displayName: 'To', type: 'STRING', required: true, description: 'Recipient email addresses (comma-separated)' },
                { name: 'subject', displayName: 'Subject', type: 'STRING', required: true },
                { name: 'body', displayName: 'Body', type: 'CODE', required: true, language: 'html', description: 'Email body (HTML supported)' },
                { name: 'cc', displayName: 'CC', type: 'STRING', required: false },
                { name: 'bcc', displayName: 'BCC', type: 'STRING', required: false }
            ],
            'LogProcessor': [
                { name: 'level', displayName: 'Log Level', type: 'SELECT', required: true, options: [
                    { value: 'DEBUG', label: 'Debug' }, { value: 'INFO', label: 'Info' },
                    { value: 'WARN', label: 'Warning' }, { value: 'ERROR', label: 'Error' }
                ]},
                { name: 'message', displayName: 'Message', type: 'EXPRESSION', required: true, description: 'Log message (supports expressions)' }
            ],
            'DelayProcessor': [
                { name: 'duration', displayName: 'Duration', type: 'DURATION', required: true, description: 'Delay duration' }
            ],
            'TransformProcessor': [
                { name: 'transformations', displayName: 'Transformations', type: 'JSON', required: true, description: 'JSON transformation rules' }
            ]
        };

        return defaults[processorName] || [];
    }

    onStepFieldChange(stepId, fieldName, value) {
        // Real-time update of step configuration
        const step = this.steps.get(stepId);
        if (step) {
            if (!step.inputConfig) step.inputConfig = {};
            step.inputConfig[fieldName] = value;
        }
    }

    onStepFieldValidate(stepId, fieldName, isValid, message) {
        // Update step validation state
        if (!this.stepValidationState) this.stepValidationState = new Map();

        let stepState = this.stepValidationState.get(stepId) || {};
        stepState[fieldName] = { isValid, message };
        this.stepValidationState.set(stepId, stepState);

        // Update step visual indicator
        this.updateStepValidationIndicator(stepId);
    }

    updateStepValidationIndicator(stepId) {
        const stepState = this.stepValidationState?.get(stepId);
        const stepEl = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);

        if (!stepEl) return;

        const hasErrors = stepState && Object.values(stepState).some(v => !v.isValid);
        const rect = stepEl.querySelector('rect');

        if (hasErrors) {
            rect.classList.add('step-has-errors');
        } else {
            rect.classList.remove('step-has-errors');
        }
    }

    saveStepProperties() {
        const step = this.steps.get(this.currentEditingStep);
        if (!step) return;

        // Validate step name
        const stepNameInput = document.getElementById('stepName');
        const stepName = stepNameInput.value.trim();

        if (!stepName) {
            stepNameInput.classList.add('is-invalid');
            stepNameInput.focus();
            return;
        }

        // Validate form fields using FormBuilder
        if (this.currentFormBuilder) {
            const validation = this.currentFormBuilder.validateAll(true);
            if (!validation.valid) {
                return; // FormBuilder shows errors
            }
            step.inputConfig = this.currentFormBuilder.getValues();
        }

        // Update step name
        step.name = stepName;

        // Update step display
        const g = this.stepsLayer.querySelector(`[data-step-id="${this.currentEditingStep}"]`);
        if (g) {
            g.querySelector('.step-name').textContent = step.name;
        }

        // Save to history
        this.saveToHistory('updateStep', { stepId: this.currentEditingStep, step: { ...step } });

        // Run workflow validation in background
        this.runValidation();

        // Close modal
        bootstrap.Modal.getInstance(document.getElementById('stepModal')).hide();
        this.currentFormBuilder = null;
    }

    // ============================================
    // Workflow Validation Integration
    // ============================================

    runValidation() {
        if (!this.workflowValidator) {
            this.workflowValidator = new WorkflowValidator();
        }

        // Build workflow object for validation
        const workflow = this.buildWorkflowForValidation();
        const result = this.workflowValidator.validateWorkflow(workflow);

        // Update validation UI
        this.updateValidationUI(result);
        this.updateStepValidationIndicators(result);

        return result;
    }

    buildWorkflowForValidation() {
        const steps = [];
        this.steps.forEach((step, id) => {
            steps.push({
                id,
                name: step.name,
                processorName: step.processor?.processorName,
                inputConfig: step.inputConfig || {},
                position: step.position
            });
        });

        const connections = [];
        this.connections.forEach((conn, id) => {
            connections.push({
                id,
                sourceStepId: conn.from,
                targetStepId: conn.to,
                connectionType: conn.type || 'DEFAULT',
                sourcePort: conn.sourcePort,
                targetPort: conn.targetPort
            });
        });

        return {
            name: this.workflow?.name || 'Untitled',
            startStepId: this.workflow?.startStepId,
            steps,
            connections
        };
    }

    updateValidationUI(result) {
        const panel = document.getElementById('validationPanel');
        const errorCount = document.getElementById('errorCount');
        const warningCount = document.getElementById('warningCount');
        const validationList = document.getElementById('validationList');

        if (!panel) return;

        const errors = result.errors || [];
        const warnings = result.warnings || [];

        errorCount.textContent = errors.length;
        warningCount.textContent = warnings.length;

        if (errors.length === 0 && warnings.length === 0) {
            panel.classList.remove('show');
            return;
        }

        let html = '';

        errors.forEach(err => {
            html += `
                <div class="validation-item validation-error" data-step-id="${err.stepId || ''}">
                    <i class="bi bi-x-circle"></i>
                    <div class="validation-item-content">
                        <strong>${err.code}</strong>: ${err.message}
                        ${err.stepName ? `<small class="d-block text-muted">Step: ${err.stepName}</small>` : ''}
                    </div>
                    ${err.stepId ? `<button class="btn btn-sm btn-link" onclick="designer.focusOnStep('${err.stepId}')">
                        <i class="bi bi-eye"></i>
                    </button>` : ''}
                </div>
            `;
        });

        warnings.forEach(warn => {
            html += `
                <div class="validation-item validation-warning" data-step-id="${warn.stepId || ''}">
                    <i class="bi bi-exclamation-triangle"></i>
                    <div class="validation-item-content">
                        <strong>${warn.code}</strong>: ${warn.message}
                        ${warn.stepName ? `<small class="d-block text-muted">Step: ${warn.stepName}</small>` : ''}
                    </div>
                    ${warn.stepId ? `<button class="btn btn-sm btn-link" onclick="designer.focusOnStep('${warn.stepId}')">
                        <i class="bi bi-eye"></i>
                    </button>` : ''}
                </div>
            `;
        });

        validationList.innerHTML = html;
        panel.classList.add('show');
    }

    updateStepValidationIndicators(result) {
        // Clear all error indicators
        this.stepsLayer.querySelectorAll('rect.step-has-errors').forEach(rect => {
            rect.classList.remove('step-has-errors');
        });

        // Mark steps with errors
        const stepErrors = result.stepErrors || {};
        Object.keys(stepErrors).forEach(stepId => {
            const stepEl = this.stepsLayer.querySelector(`[data-step-id="${stepId}"]`);
            if (stepEl) {
                const rect = stepEl.querySelector('rect');
                if (rect) rect.classList.add('step-has-errors');
            }
        });

        // Mark connections with errors
        this.connectionsLayer.querySelectorAll('path').forEach(path => {
            path.classList.remove('connection-error');
        });

        const connectionErrors = result.connectionErrors || {};
        Object.keys(connectionErrors).forEach(connId => {
            const conn = this.connectionsLayer.querySelector(`[data-connection-id="${connId}"]`);
            if (conn) conn.classList.add('connection-error');
        });
    }

    focusOnStep(stepId) {
        const step = this.steps.get(stepId);
        if (!step) return;

        // Pan to center the step
        const containerRect = this.canvasContainer.getBoundingClientRect();
        const centerX = containerRect.width / 2;
        const centerY = containerRect.height / 2;

        this.panX = centerX - (step.position.x + 100) * this.zoom;
        this.panY = centerY - (step.position.y + 40) * this.zoom;

        this.updateCanvasTransform();
        this.selectStep(stepId);
        this.updateMinimap();
    }

    hideValidationPanel() {
        const panel = document.getElementById('validationPanel');
        if (panel) panel.classList.remove('show');
    }

    // ============================================
    // Undo/Redo
    // ============================================

    saveToHistory(action, data) {
        // Remove any redo states
        if (this.historyIndex < this.history.length - 1) {
            this.history = this.history.slice(0, this.historyIndex + 1);
        }

        this.history.push({ action, data, timestamp: Date.now() });

        if (this.history.length > this.maxHistory) {
            this.history.shift();
        } else {
            this.historyIndex++;
        }

        this.updateUndoRedoButtons();
    }

    undo() {
        if (this.historyIndex < 0) return;

        const item = this.history[this.historyIndex];
        this.applyUndo(item);
        this.historyIndex--;
        this.updateUndoRedoButtons();
    }

    redo() {
        if (this.historyIndex >= this.history.length - 1) return;

        this.historyIndex++;
        const item = this.history[this.historyIndex];
        this.applyRedo(item);
        this.updateUndoRedoButtons();
    }

    applyUndo(item) {
        switch (item.action) {
            case 'add_step':
                this.steps.delete(item.data.step.id);
                const g = this.stepsLayer.querySelector(`[data-step-id="${item.data.step.id}"]`);
                if (g) g.remove();
                break;
            case 'delete_step':
                this.steps.set(item.data.step.id, item.data.step);
                this.renderStep(item.data.step);
                item.data.connections?.forEach(conn => {
                    this.connections.set(conn.id, conn);
                    this.renderConnection(conn);
                });
                break;
            case 'move_step':
                const step = this.steps.get(item.data.stepId);
                if (step) {
                    step.position.x = item.data.oldPosition.x;
                    step.position.y = item.data.oldPosition.y;
                    const sg = this.stepsLayer.querySelector(`[data-step-id="${item.data.stepId}"]`);
                    if (sg) {
                        sg.setAttribute('transform', `translate(${step.position.x}, ${step.position.y})`);
                    }
                    this.updateConnectionsForStep(item.data.stepId);
                }
                break;
            case 'add_connection':
                this.connections.delete(item.data.connection.id);
                const cg = this.connectionsLayer.querySelector(`[data-connection-id="${item.data.connection.id}"]`);
                if (cg) cg.remove();
                break;
            case 'delete_connection':
                this.connections.set(item.data.connection.id, item.data.connection);
                this.renderConnection(item.data.connection);
                break;
        }
        this.updateMinimap();
    }

    applyRedo(item) {
        switch (item.action) {
            case 'add_step':
                this.steps.set(item.data.step.id, item.data.step);
                this.renderStep(item.data.step);
                break;
            case 'delete_step':
                this.steps.delete(item.data.step.id);
                const g = this.stepsLayer.querySelector(`[data-step-id="${item.data.step.id}"]`);
                if (g) g.remove();
                item.data.connections?.forEach(conn => {
                    this.connections.delete(conn.id);
                    const cg = this.connectionsLayer.querySelector(`[data-connection-id="${conn.id}"]`);
                    if (cg) cg.remove();
                });
                break;
            case 'move_step':
                const step = this.steps.get(item.data.stepId);
                if (step) {
                    step.position.x = item.data.newPosition.x;
                    step.position.y = item.data.newPosition.y;
                    const sg = this.stepsLayer.querySelector(`[data-step-id="${item.data.stepId}"]`);
                    if (sg) {
                        sg.setAttribute('transform', `translate(${step.position.x}, ${step.position.y})`);
                    }
                    this.updateConnectionsForStep(item.data.stepId);
                }
                break;
            case 'add_connection':
                this.connections.set(item.data.connection.id, item.data.connection);
                this.renderConnection(item.data.connection);
                break;
            case 'delete_connection':
                this.connections.delete(item.data.connection.id);
                const cg = this.connectionsLayer.querySelector(`[data-connection-id="${item.data.connection.id}"]`);
                if (cg) cg.remove();
                break;
        }
        this.updateMinimap();
    }

    updateUndoRedoButtons() {
        const btnUndo = document.getElementById('btnUndo');
        const btnRedo = document.getElementById('btnRedo');
        if (btnUndo) btnUndo.disabled = this.historyIndex < 0;
        if (btnRedo) btnRedo.disabled = this.historyIndex >= this.history.length - 1;
    }

    // ============================================
    // Workflow Operations
    // ============================================

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
        // Run client-side validation using WorkflowValidator
        const result = this.runValidation();

        // Also try server-side validation if available
        try {
            const versionData = this.buildWorkflowForValidation();
            const serverResult = await api.validateWorkflow(versionData);

            // Merge server and client results
            const mergedResult = {
                valid: result.valid && serverResult.valid,
                errors: [...(result.errors || []), ...(serverResult.errors || [])],
                warnings: [...(result.warnings || []), ...(serverResult.warnings || [])],
                stepErrors: { ...(result.stepErrors || {}), ...(serverResult.stepErrors || {}) }
            };

            this.showValidationResults(mergedResult);
            this.updateStepValidationIndicators(mergedResult);
        } catch (error) {
            // If API fails, show client-side validation results
            this.showValidationResults(result);
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
}

// Utility function
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Initialize designer
let designer;
document.addEventListener('DOMContentLoaded', () => {
    designer = new WorkflowDesigner();
});
