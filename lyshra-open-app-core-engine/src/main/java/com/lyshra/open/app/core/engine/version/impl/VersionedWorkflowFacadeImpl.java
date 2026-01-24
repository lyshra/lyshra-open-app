package com.lyshra.open.app.core.engine.version.impl;

import com.lyshra.open.app.core.engine.version.IVersionAwareWorkflowExecutor;
import com.lyshra.open.app.core.engine.version.IVersionedWorkflowFacade;
import com.lyshra.open.app.core.engine.version.IWorkflowExecutionBindingStore;
import com.lyshra.open.app.core.engine.version.IWorkflowVersionRegistryService;
import com.lyshra.open.app.core.engine.version.migration.IWorkflowMigrationExecutor;
import com.lyshra.open.app.core.engine.version.migration.impl.WorkflowMigrationExecutorImpl;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of versioned workflow facade.
 * Provides centralized access to all versioning components.
 */
@Slf4j
public class VersionedWorkflowFacadeImpl implements IVersionedWorkflowFacade {

    private final IWorkflowVersionRegistryService versionRegistry;
    private final IWorkflowExecutionBindingStore bindingStore;
    private final IWorkflowMigrationExecutor migrationExecutor;
    private final IVersionAwareWorkflowExecutor versionAwareExecutor;

    private VersionedWorkflowFacadeImpl() {
        this.versionRegistry = WorkflowVersionRegistryImpl.getInstance();
        this.bindingStore = InMemoryWorkflowExecutionBindingStoreImpl.getInstance();
        this.migrationExecutor = WorkflowMigrationExecutorImpl.getInstance();
        this.versionAwareExecutor = VersionAwareWorkflowExecutorImpl.getInstance();
        log.info("Versioned Workflow Facade initialized");
    }

    private static final class SingletonHelper {
        private static final VersionedWorkflowFacadeImpl INSTANCE = new VersionedWorkflowFacadeImpl();
    }

    public static IVersionedWorkflowFacade getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public IWorkflowVersionRegistryService getVersionRegistry() {
        return versionRegistry;
    }

    @Override
    public IWorkflowExecutionBindingStore getBindingStore() {
        return bindingStore;
    }

    @Override
    public IWorkflowMigrationExecutor getMigrationExecutor() {
        return migrationExecutor;
    }

    @Override
    public IVersionAwareWorkflowExecutor getVersionAwareExecutor() {
        return versionAwareExecutor;
    }
}
