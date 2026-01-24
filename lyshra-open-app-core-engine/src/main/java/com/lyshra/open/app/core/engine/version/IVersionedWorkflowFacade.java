package com.lyshra.open.app.core.engine.version;

import com.lyshra.open.app.core.engine.version.migration.IWorkflowMigrationExecutor;

/**
 * Facade interface providing access to all versioning services.
 * Centralizes access to version registry, execution binding, and migration.
 *
 * <p>Design Pattern: Facade pattern for simplified access to versioning subsystem.</p>
 */
public interface IVersionedWorkflowFacade {

    /**
     * Gets the workflow version registry.
     *
     * @return version registry service
     */
    IWorkflowVersionRegistryService getVersionRegistry();

    /**
     * Gets the execution binding store.
     *
     * @return binding store
     */
    IWorkflowExecutionBindingStore getBindingStore();

    /**
     * Gets the migration executor.
     *
     * @return migration executor
     */
    IWorkflowMigrationExecutor getMigrationExecutor();

    /**
     * Gets the version-aware workflow executor.
     *
     * @return version-aware executor
     */
    IVersionAwareWorkflowExecutor getVersionAwareExecutor();
}
