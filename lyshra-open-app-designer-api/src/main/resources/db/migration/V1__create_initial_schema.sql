-- ============================================
-- Lyshra OpenApp Workflow Designer
-- Initial Database Schema
-- Version: 1.0.0
-- ============================================

-- ============================================
-- Users Table
-- Stores user accounts for authentication
-- ============================================
CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    roles VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_locked BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

-- ============================================
-- Workflow Definitions Table
-- Stores workflow metadata and lifecycle state
-- ============================================
CREATE TABLE workflow_definitions (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    organization VARCHAR(100) NOT NULL,
    module VARCHAR(100) NOT NULL,
    category VARCHAR(100),
    tags TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_version_id VARCHAR(36),
    lifecycle_state VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_lifecycle_state CHECK (lifecycle_state IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'ARCHIVED'))
);

CREATE INDEX idx_workflow_def_organization ON workflow_definitions(organization);
CREATE INDEX idx_workflow_def_module ON workflow_definitions(organization, module);
CREATE INDEX idx_workflow_def_state ON workflow_definitions(lifecycle_state);
CREATE INDEX idx_workflow_def_name ON workflow_definitions(name);
CREATE INDEX idx_workflow_def_category ON workflow_definitions(category);
CREATE INDEX idx_workflow_def_updated ON workflow_definitions(updated_at DESC);

-- Unique constraint on name within organization/module
CREATE UNIQUE INDEX idx_workflow_def_unique_name ON workflow_definitions(organization, module, name);

-- ============================================
-- Workflow Versions Table
-- Stores versioned workflow configurations
-- Steps and connections stored as JSON for flexibility
-- ============================================
CREATE TABLE workflow_versions (
    id VARCHAR(36) PRIMARY KEY,
    workflow_definition_id VARCHAR(36) NOT NULL,
    version_number VARCHAR(20) NOT NULL,
    description TEXT,
    start_step_id VARCHAR(36),
    steps_json TEXT,
    connections_json TEXT,
    context_retention_json TEXT,
    designer_metadata_json TEXT,
    state VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_by VARCHAR(100),
    activated_at TIMESTAMP WITH TIME ZONE,
    deprecated_by VARCHAR(100),
    deprecated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_version_definition FOREIGN KEY (workflow_definition_id)
        REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    CONSTRAINT chk_version_state CHECK (state IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'ARCHIVED'))
);

CREATE INDEX idx_workflow_ver_definition ON workflow_versions(workflow_definition_id);
CREATE INDEX idx_workflow_ver_state ON workflow_versions(state);
CREATE INDEX idx_workflow_ver_number ON workflow_versions(workflow_definition_id, version_number);
CREATE INDEX idx_workflow_ver_created ON workflow_versions(created_at DESC);

-- Unique constraint on version number within a workflow
CREATE UNIQUE INDEX idx_workflow_ver_unique ON workflow_versions(workflow_definition_id, version_number);

-- ============================================
-- Workflow Executions Table
-- Tracks workflow execution instances
-- ============================================
CREATE TABLE workflow_executions (
    id VARCHAR(36) PRIMARY KEY,
    workflow_definition_id VARCHAR(36) NOT NULL,
    workflow_version_id VARCHAR(36) NOT NULL,
    workflow_name VARCHAR(255),
    version_number VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    correlation_id VARCHAR(255),
    triggered_by VARCHAR(100),
    input_data_json TEXT,
    output_data_json TEXT,
    context_data_json TEXT,
    current_step_id VARCHAR(36),
    current_step_name VARCHAR(255),
    error_code VARCHAR(100),
    error_message TEXT,
    step_logs_json TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    parent_execution_id VARCHAR(36),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_execution_definition FOREIGN KEY (workflow_definition_id)
        REFERENCES workflow_definitions(id),
    CONSTRAINT fk_execution_version FOREIGN KEY (workflow_version_id)
        REFERENCES workflow_versions(id),
    CONSTRAINT fk_execution_parent FOREIGN KEY (parent_execution_id)
        REFERENCES workflow_executions(id),
    CONSTRAINT chk_execution_status CHECK (status IN (
        'PENDING', 'SCHEDULED', 'RUNNING', 'PAUSED', 'WAITING',
        'COMPLETED', 'FAILED', 'CANCELLED', 'ABORTED', 'TIMED_OUT'
    ))
);

CREATE INDEX idx_execution_definition ON workflow_executions(workflow_definition_id);
CREATE INDEX idx_execution_version ON workflow_executions(workflow_version_id);
CREATE INDEX idx_execution_status ON workflow_executions(status);
CREATE INDEX idx_execution_correlation ON workflow_executions(correlation_id);
CREATE INDEX idx_execution_started ON workflow_executions(started_at DESC);
CREATE INDEX idx_execution_parent ON workflow_executions(parent_execution_id);

-- Index for finding running executions
CREATE INDEX idx_execution_running ON workflow_executions(status)
    WHERE status IN ('PENDING', 'RUNNING');

-- ============================================
-- Add foreign key from workflow_definitions to workflow_versions
-- for the active version reference
-- ============================================
ALTER TABLE workflow_definitions
    ADD CONSTRAINT fk_definition_active_version
    FOREIGN KEY (active_version_id)
    REFERENCES workflow_versions(id)
    ON DELETE SET NULL;

-- ============================================
-- Comments for documentation
-- ============================================
COMMENT ON TABLE users IS 'User accounts for authentication and authorization';
COMMENT ON TABLE workflow_definitions IS 'Workflow definitions with lifecycle management';
COMMENT ON TABLE workflow_versions IS 'Versioned workflow configurations with steps and connections';
COMMENT ON TABLE workflow_executions IS 'Workflow execution history and status tracking';

COMMENT ON COLUMN workflow_definitions.tags IS 'JSON object containing key-value pairs for categorization';
COMMENT ON COLUMN workflow_versions.steps_json IS 'JSON array of workflow step definitions';
COMMENT ON COLUMN workflow_versions.connections_json IS 'JSON object mapping connection IDs to connection definitions';
COMMENT ON COLUMN workflow_versions.designer_metadata_json IS 'JSON object containing canvas position, zoom level, etc.';
COMMENT ON COLUMN workflow_executions.input_data_json IS 'JSON object containing execution input parameters';
COMMENT ON COLUMN workflow_executions.output_data_json IS 'JSON object containing execution output data';
COMMENT ON COLUMN workflow_executions.step_logs_json IS 'JSON array of step execution logs';
