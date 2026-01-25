# Lyshra OpenApp Workflow Designer - UI Requirements Document

**Version:** 1.0.0
**Last Updated:** 2024
**Status:** Final

---

## Table of Contents

1. [Overview](#1-overview)
2. [User Roles and Permissions](#2-user-roles-and-permissions)
3. [Workflow Designer Requirements](#3-workflow-designer-requirements)
4. [Monitoring Dashboard Requirements](#4-monitoring-dashboard-requirements)
5. [Component Model](#5-component-model)
6. [User Experience Guidelines](#6-user-experience-guidelines)
7. [Accessibility Requirements](#7-accessibility-requirements)
8. [Performance Requirements](#8-performance-requirements)

---

## 1. Overview

### 1.1 Purpose

The Lyshra OpenApp Workflow Designer provides a visual interface for creating, editing, and managing business process workflows. The monitoring dashboard enables real-time tracking of workflow executions.

### 1.2 Scope

This document covers:
- Visual workflow designer canvas and interactions
- Processor palette and configuration
- Version management interface
- Execution monitoring dashboard
- Authentication and authorization UI

### 1.3 Target Users

| User Type | Description | Primary Use Cases |
|-----------|-------------|-------------------|
| Business Analysts | Design workflows visually | Create, edit workflows |
| Developers | Configure complex processors | Advanced configuration, debugging |
| Operations | Monitor executions | Real-time monitoring, error handling |
| Administrators | Manage users and system | User management, system configuration |

---

## 2. User Roles and Permissions

### 2.1 Role Definitions

| Role | Description |
|------|-------------|
| **ADMIN** | Full system access including user management |
| **DEVELOPER** | Create, edit, delete workflows and versions |
| **OPERATOR** | View workflows, execute, and monitor |
| **VIEWER** | Read-only access to workflows and executions |

### 2.2 Permission Matrix

| Action | ADMIN | DEVELOPER | OPERATOR | VIEWER |
|--------|-------|-----------|----------|--------|
| View workflows | ✓ | ✓ | ✓ | ✓ |
| Create workflow | ✓ | ✓ | ✗ | ✗ |
| Edit workflow | ✓ | ✓ | ✗ | ✗ |
| Delete workflow | ✓ | ✓ | ✗ | ✗ |
| Activate version | ✓ | ✓ | ✗ | ✗ |
| Execute workflow | ✓ | ✓ | ✓ | ✗ |
| Cancel execution | ✓ | ✓ | ✓ | ✗ |
| View executions | ✓ | ✓ | ✓ | ✓ |
| Manage users | ✓ | ✗ | ✗ | ✗ |

---

## 3. Workflow Designer Requirements

### 3.1 Canvas Requirements

#### 3.1.1 Canvas Viewport

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| CANVAS-001 | Infinite scrollable canvas with minimum 4000x3000 pixels | High |
| CANVAS-002 | Grid-based layout with configurable grid size (10-50px) | Medium |
| CANVAS-003 | Snap-to-grid functionality (toggleable) | Medium |
| CANVAS-004 | Zoom levels: 25%, 50%, 75%, 100%, 125%, 150%, 200% | High |
| CANVAS-005 | Pan/scroll with mouse drag or keyboard arrows | High |
| CANVAS-006 | Fit-to-view button to auto-center workflow | Medium |
| CANVAS-007 | Mini-map navigation for large workflows | Low |

#### 3.1.2 Visual Indicators

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| CANVAS-008 | Grid dots/lines pattern for visual guidance | Medium |
| CANVAS-009 | Selection highlight with distinct border color | High |
| CANVAS-010 | Start step indicator (green dot/badge) | High |
| CANVAS-011 | Error step indicator (red border) | High |
| CANVAS-012 | Connection hover highlighting | Medium |

### 3.2 Step (Node) Requirements

#### 3.2.1 Step Visual Representation

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| STEP-001 | Rectangular shape with rounded corners (8px radius) | High |
| STEP-002 | Default size: 180x60 pixels, resizable | Medium |
| STEP-003 | Display processor icon (left side) | Medium |
| STEP-004 | Display step name (primary text, max 25 chars) | High |
| STEP-005 | Display processor type (secondary text) | Medium |
| STEP-006 | Connection ports: right side (output), left side (input) | High |
| STEP-007 | Visual state: normal, selected, hover, error, disabled | High |

#### 3.2.2 Step Interactions

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| STEP-008 | Drag to reposition on canvas | High |
| STEP-009 | Click to select | High |
| STEP-010 | Double-click to open configuration | High |
| STEP-011 | Right-click for context menu | Medium |
| STEP-012 | Multi-select with Ctrl+Click or selection box | Medium |
| STEP-013 | Delete with Delete key or context menu | High |
| STEP-014 | Duplicate with Ctrl+D or context menu | Medium |

#### 3.2.3 Step Context Menu

| Menu Item | Action | Keyboard Shortcut |
|-----------|--------|-------------------|
| Edit Step | Open configuration modal | Enter |
| Delete | Remove step and connections | Delete |
| Set as Start | Mark as workflow entry point | - |
| Duplicate | Create copy of step | Ctrl+D |
| Copy | Copy to clipboard | Ctrl+C |
| Paste | Paste from clipboard | Ctrl+V |

### 3.3 Connection Requirements

#### 3.3.1 Connection Visual Representation

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| CONN-001 | Bezier curve path between steps | High |
| CONN-002 | Arrow marker at target end | High |
| CONN-003 | Default color: gray (#6c757d) | Medium |
| CONN-004 | Conditional branch label display | High |
| CONN-005 | Different colors for connection types (normal, conditional, error) | Medium |

#### 3.3.2 Connection Types

| Type | Color | Description |
|------|-------|-------------|
| NORMAL | Gray (#6c757d) | Default flow |
| CONDITIONAL | Blue (#0d6efd) | Branch based on condition |
| ERROR_FALLBACK | Red (#dc3545) | Error handling path |

#### 3.3.3 Connection Interactions

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| CONN-006 | Drag from source port to create new connection | High |
| CONN-007 | Click to select connection | Medium |
| CONN-008 | Double-click to edit branch condition | Medium |
| CONN-009 | Delete key to remove selected connection | High |
| CONN-010 | Hover to highlight source and target steps | Medium |

### 3.4 Processor Palette Requirements

#### 3.4.1 Palette Layout

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| PAL-001 | Collapsible sidebar (left side, 280px width) | High |
| PAL-002 | Search/filter input at top | High |
| PAL-003 | Grouped by category (collapsible sections) | High |
| PAL-004 | Alphabetical sorting within categories | Medium |

#### 3.4.2 Processor Item Display

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| PAL-005 | Icon representing processor type | Medium |
| PAL-006 | Display name (primary text) | High |
| PAL-007 | Brief description (secondary text, max 50 chars) | Medium |
| PAL-008 | Tooltip with full description on hover | Medium |

#### 3.4.3 Processor Categories

| Category | Description | Example Processors |
|----------|-------------|-------------------|
| Control Flow | Branching and routing | IF, SWITCH |
| Scripting | Code execution | JavaScript |
| Integration | External systems | API, HTTP |
| Data Operations | List/data manipulation | Filter, Sort, Map |
| Date Operations | Date/time processing | DateAdd, DateCompare |
| Database | Database operations | MongoDB processors |

### 3.5 Properties Panel Requirements

#### 3.5.1 Panel Layout

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| PROP-001 | Right sidebar (320px width, collapsible) | High |
| PROP-002 | Context-sensitive: shows selected step properties | High |
| PROP-003 | Grouped sections: General, Configuration, Error Handling | High |
| PROP-004 | Inline validation with error messages | High |

#### 3.5.2 Field Types

| Field Type | UI Component | Description |
|------------|--------------|-------------|
| STRING | Text input | Single-line text |
| NUMBER | Number input | Numeric values with validation |
| BOOLEAN | Toggle/Checkbox | True/false values |
| SELECT | Dropdown | Single selection from options |
| MULTISELECT | Multi-dropdown | Multiple selections |
| TEXTAREA | Multiline input | Long text content |
| CODE | Code editor | Syntax-highlighted code |
| JSON | JSON editor | Structured JSON input |
| EXPRESSION | Expression input | SpEL/JavaScript expressions |
| DATE | Date picker | Date selection |
| DATETIME | DateTime picker | Date and time selection |
| DURATION | Duration input | Time duration (ISO 8601) |
| MAP | Key-value editor | Dynamic key-value pairs |
| LIST | List editor | Dynamic list of values |

### 3.6 Toolbar Requirements

| Button | Icon | Action | Shortcut |
|--------|------|--------|----------|
| New | file-plus | Create new workflow | Ctrl+N |
| Open | folder-open | Open existing workflow | Ctrl+O |
| Save | save | Save current workflow | Ctrl+S |
| Undo | arrow-counterclockwise | Undo last action | Ctrl+Z |
| Redo | arrow-clockwise | Redo last undone action | Ctrl+Y |
| Zoom In | zoom-in | Increase zoom level | Ctrl++ |
| Zoom Out | zoom-out | Decrease zoom level | Ctrl+- |
| Fit View | arrows-fullscreen | Fit workflow to view | Ctrl+0 |
| Validate | check-circle | Validate workflow | Ctrl+Shift+V |
| Execute | play-fill | Execute workflow | F5 |

### 3.7 Version Management Requirements

#### 3.7.1 Version List

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| VER-001 | Display version number, state, created date | High |
| VER-002 | Visual indicator for active version | High |
| VER-003 | Compare versions (side-by-side diff) | Low |
| VER-004 | Version notes/description | Medium |

#### 3.7.2 Version Actions

| Action | Description | Availability |
|--------|-------------|--------------|
| Create | Create new version from current | DRAFT, DEPRECATED versions |
| Activate | Set as active version | DRAFT, DEPRECATED versions |
| Deprecate | Mark as deprecated | ACTIVE version only |
| Rollback | Revert to previous version | Any non-active version |
| Delete | Remove version | DRAFT versions only |

### 3.8 Validation Requirements

#### 3.8.1 Validation Checks

| Check Type | Description | Severity |
|------------|-------------|----------|
| Missing Start Step | No start step defined | Error |
| Invalid Start Step | Start step doesn't exist | Error |
| Orphaned Steps | Steps not connected to flow | Warning |
| Cyclic Connections | Potential infinite loops | Warning |
| Missing Required Fields | Required configuration missing | Error |
| Invalid Processor Reference | Processor not found | Error |
| Invalid Fallback Step | Error handler references invalid step | Error |

#### 3.8.2 Validation UI

| Requirement ID | Description | Priority |
|----------------|-------------|----------|
| VAL-001 | Validation modal with errors/warnings list | High |
| VAL-002 | Click error to navigate to problematic step | High |
| VAL-003 | Real-time validation as user edits | Medium |
| VAL-004 | Success indicator when valid | High |

---

## 4. Monitoring Dashboard Requirements

### 4.1 Dashboard Overview

#### 4.1.1 Statistics Cards

| Card | Metric | Update Frequency |
|------|--------|------------------|
| Total Workflows | Count of all workflows | On load, manual refresh |
| Running Executions | Currently running count | Real-time (SSE) |
| Completed Today | Executions completed today | Every 30 seconds |
| Success Rate | Percentage of successful executions | Every 30 seconds |

#### 4.1.2 Time Filters

| Filter | Description |
|--------|-------------|
| Today | Current day |
| Last 7 Days | Past week |
| Last 30 Days | Past month |
| Custom Range | User-defined date range |

### 4.2 Execution List Requirements

#### 4.2.1 Table Columns

| Column | Description | Sortable | Filterable |
|--------|-------------|----------|------------|
| ID | Execution identifier | No | Yes |
| Workflow | Workflow name | Yes | Yes |
| Version | Version number | Yes | Yes |
| Status | Execution status | Yes | Yes |
| Started | Start timestamp | Yes | Yes |
| Duration | Execution duration | Yes | No |
| Triggered By | User who started | Yes | Yes |
| Actions | Action buttons | No | No |

#### 4.2.2 Status Values

| Status | Color | Description |
|--------|-------|-------------|
| PENDING | Gray | Awaiting execution |
| RUNNING | Blue (animated) | Currently executing |
| WAITING | Cyan | Waiting for external event |
| RETRYING | Orange | Retrying after failure |
| COMPLETED | Green | Successfully completed |
| FAILED | Red | Execution failed |
| ABORTED | Dark Red | Forcefully stopped |
| CANCELLED | Yellow | User cancelled |

#### 4.2.3 Execution Actions

| Action | Availability | Description |
|--------|--------------|-------------|
| View Details | All | Open detail modal |
| Cancel | RUNNING, PENDING, WAITING | Stop execution |
| Retry | FAILED | Start new execution with same input |
| Download Logs | All | Export execution logs |

### 4.3 Execution Detail Modal

#### 4.3.1 General Information

| Field | Description |
|-------|-------------|
| Execution ID | Unique identifier |
| Workflow Name | Name of the workflow |
| Version | Version number executed |
| Status | Current status with badge |
| Triggered By | User who initiated |
| Correlation ID | External correlation identifier |

#### 4.3.2 Timing Information

| Field | Description |
|-------|-------------|
| Started At | Execution start timestamp |
| Completed At | Execution end timestamp |
| Duration | Total execution time |
| Current Step | Currently executing step (if running) |

#### 4.3.3 Data Views

| View | Description |
|------|-------------|
| Input Data | JSON viewer for input data |
| Output Data | JSON viewer for output data |
| Variables | JSON viewer for context variables |
| Error Details | Error message, code, and stack trace |

#### 4.3.4 Step Execution Log

| Column | Description |
|--------|-------------|
| Step Name | Name of the step |
| Status | Step execution status |
| Started | Step start timestamp |
| Duration | Step execution duration |
| Branch | Output branch taken |
| Attempt | Retry attempt number |

### 4.4 Real-Time Updates

#### 4.4.1 SSE Event Types

| Event Type | Data | Description |
|------------|------|-------------|
| execution.started | ExecutionDTO | New execution started |
| execution.step.started | StepLogDTO | Step began execution |
| execution.step.completed | StepLogDTO | Step finished |
| execution.completed | ExecutionDTO | Execution finished successfully |
| execution.failed | ExecutionDTO | Execution failed |
| execution.cancelled | ExecutionDTO | Execution was cancelled |

#### 4.4.2 UI Update Behavior

| Event | UI Action |
|-------|-----------|
| New Execution | Add to list, update running count |
| Status Change | Update row status, animate transition |
| Completion | Move to completed, update statistics |
| Failure | Highlight row, show error indicator |

---

## 5. Component Model

### 5.1 Workflow Definition Model

```
WorkflowDefinition
├── id: string (UUID)
├── name: string (1-255 chars)
├── description: string (optional, max 2000 chars)
├── organization: string (required)
├── module: string (required)
├── category: string (optional)
├── tags: Map<string, string>
├── lifecycleState: enum (DRAFT, ACTIVE, DEPRECATED, ARCHIVED)
├── activeVersionId: string (optional)
├── createdBy: string
├── createdAt: ISO8601 timestamp
├── updatedBy: string
└── updatedAt: ISO8601 timestamp
```

### 5.2 Workflow Version Model

```
WorkflowVersion
├── id: string (UUID)
├── workflowDefinitionId: string
├── versionNumber: string (e.g., "1.0.0")
├── description: string (optional)
├── startStepId: string (required)
├── steps: WorkflowStepDefinition[]
├── connections: Map<string, WorkflowConnection>
├── contextRetention: enum (FULL, LATEST)
├── state: enum (DRAFT, ACTIVE, DEPRECATED, ARCHIVED)
├── designerMetadata: DesignerMetadata
├── createdBy: string
├── createdAt: ISO8601 timestamp
├── activatedBy: string (optional)
├── activatedAt: ISO8601 timestamp (optional)
├── deprecatedBy: string (optional)
└── deprecatedAt: ISO8601 timestamp (optional)
```

### 5.3 Step Definition Model

```
WorkflowStepDefinition
├── id: string (UUID)
├── name: string (1-100 chars)
├── description: string (optional)
├── type: enum (PROCESSOR, WORKFLOW)
├── processor: ProcessorReference (if type=PROCESSOR)
│   ├── organization: string
│   ├── module: string
│   ├── version: string
│   └── processorName: string
├── workflowCall: WorkflowReference (if type=WORKFLOW)
│   ├── organization: string
│   ├── module: string
│   ├── version: string
│   └── workflowName: string
├── inputConfig: Map<string, any>
├── timeout: ISO8601 duration (optional)
├── errorHandling: StepErrorHandling (optional)
│   ├── defaultStrategy: enum (END_WORKFLOW, ABORT_WORKFLOW, USE_FALLBACK_STEP)
│   ├── fallbackStepId: string (optional)
│   ├── retryPolicy: RetryPolicy (optional)
│   │   ├── maxAttempts: integer
│   │   ├── delayMs: long
│   │   └── backoffMultiplier: double
│   └── errorHandlers: Map<string, ErrorHandlerConfig>
└── position: StepPosition
    ├── x: double
    ├── y: double
    ├── width: double
    └── height: double
```

### 5.4 Connection Model

```
WorkflowConnection
├── id: string (UUID)
├── sourceStepId: string
├── targetStepId: string
├── branchCondition: string (optional, e.g., "true", "false", "default")
├── label: string (optional)
├── type: enum (NORMAL, CONDITIONAL, ERROR_FALLBACK)
└── points: ConnectionPoint[] (optional, for curved paths)
    ├── x: double
    └── y: double
```

### 5.5 Designer Metadata Model

```
DesignerMetadata
├── canvasWidth: double (default: 2000)
├── canvasHeight: double (default: 1500)
├── zoomLevel: double (default: 1.0)
├── panX: double (default: 0)
├── panY: double (default: 0)
├── gridType: string (default: "dots")
├── snapToGrid: boolean (default: true)
└── gridSize: integer (default: 20)
```

### 5.6 Processor Metadata Model

```
ProcessorMetadata
├── pluginOrganization: string
├── pluginModule: string
├── pluginVersion: string
├── processorName: string
├── displayName: string
├── description: string
├── category: string
├── icon: string (Bootstrap icon name)
├── inputFields: ProcessorInputField[]
│   ├── name: string
│   ├── displayName: string
│   ├── description: string
│   ├── type: enum (STRING, NUMBER, BOOLEAN, SELECT, ...)
│   ├── required: boolean
│   ├── defaultValue: any
│   ├── options: FieldOption[] (for SELECT/MULTISELECT)
│   │   ├── value: string
│   │   ├── label: string
│   │   └── description: string
│   ├── validation: Map<string, any>
│   └── expressionSupport: string (optional)
├── possibleBranches: string[]
└── sampleConfig: Map<string, any>
```

### 5.7 Execution Model

```
WorkflowExecution
├── id: string (UUID)
├── workflowDefinitionId: string
├── workflowVersionId: string
├── workflowName: string
├── versionNumber: string
├── status: enum (PENDING, RUNNING, WAITING, RETRYING, COMPLETED, FAILED, ABORTED, CANCELLED)
├── startedAt: ISO8601 timestamp
├── completedAt: ISO8601 timestamp (optional)
├── currentStepId: string (optional)
├── currentStepName: string (optional)
├── inputData: Map<string, any>
├── outputData: Map<string, any> (optional)
├── variables: Map<string, any>
├── errorMessage: string (optional)
├── errorCode: string (optional)
├── stepLogs: StepExecutionLog[]
├── triggeredBy: string
├── correlationId: string (optional)
└── metadata: Map<string, string>
```

---

## 6. User Experience Guidelines

### 6.1 Visual Consistency

| Element | Specification |
|---------|---------------|
| Primary Color | #0d6efd (Bootstrap blue) |
| Success Color | #198754 (Bootstrap green) |
| Danger Color | #dc3545 (Bootstrap red) |
| Warning Color | #ffc107 (Bootstrap yellow) |
| Border Radius | 8px (cards, modals), 4px (buttons, inputs) |
| Font Family | System UI stack |
| Shadow | 0 4px 12px rgba(0,0,0,0.15) |

### 6.2 Interaction Feedback

| Action | Feedback |
|--------|----------|
| Button Click | Ripple effect + color change |
| Drag Start | Slight scale up (1.02x) |
| Drag Over Valid | Green highlight on target |
| Drag Over Invalid | Red highlight, cursor change |
| Error | Shake animation + red border |
| Success | Green check animation |
| Loading | Spinner or skeleton |

### 6.3 Responsive Behavior

| Breakpoint | Behavior |
|------------|----------|
| < 768px | Hide sidebars, show toggle buttons |
| 768px - 1024px | Collapsible sidebars |
| > 1024px | Full layout with all panels |

---

## 7. Accessibility Requirements

### 7.1 WCAG 2.1 AA Compliance

| Requirement | Description |
|-------------|-------------|
| Color Contrast | Minimum 4.5:1 for text, 3:1 for UI components |
| Keyboard Navigation | All actions accessible via keyboard |
| Screen Reader | ARIA labels on all interactive elements |
| Focus Indicators | Visible focus rings on all focusable elements |
| Error Identification | Errors identified by more than color alone |

### 7.2 Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Tab | Navigate between elements |
| Enter | Activate button/open modal |
| Escape | Close modal/cancel operation |
| Arrow Keys | Navigate within lists/canvas |
| Space | Toggle checkbox/select option |

---

## 8. Performance Requirements

### 8.1 Load Time Targets

| Metric | Target |
|--------|--------|
| Initial Page Load | < 2 seconds |
| Canvas Render | < 500ms for 100 steps |
| API Response | < 200ms for CRUD operations |
| Real-time Updates | < 100ms latency |

### 8.2 Resource Limits

| Limit | Value |
|-------|-------|
| Max Steps per Workflow | 500 |
| Max Connections per Workflow | 1000 |
| Max Concurrent Executions Display | 100 |
| Max Execution History | 1000 records |

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | 2024 | System | Initial version |
