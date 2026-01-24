package com.lyshra.open.app.core.engine;

import com.lyshra.open.app.core.engine.database.ILyshraOpenAppDatasourceEngine;
import com.lyshra.open.app.core.engine.humantask.ILyshraOpenAppHumanTaskProcessorExecutor;
import com.lyshra.open.app.core.engine.humantask.ILyshraOpenAppHumanTaskService;
import com.lyshra.open.app.core.engine.message.ILyshraOpenAppPluginMessageSource;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppProcessorExecutor;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowExecutor;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowStepExecutor;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginFactory;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginLoader;
import com.lyshra.open.app.core.engine.state.ILyshraOpenAppWorkflowStateStore;
import com.lyshra.open.app.core.engine.state.LyshraOpenAppWorkflowStateStoreManager;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;

public interface ILyshraOpenAppFacade extends ILyshraOpenAppPluginFacade {
    ILyshraOpenAppDatasourceEngine getDatasourceEngine();
    ILyshraOpenAppPluginLoader getPluginLoader();
    ILyshraOpenAppPluginFactory getPluginFactory();
    ILyshraOpenAppPluginMessageSource getCoreEngineMessageSource();
    ILyshraOpenAppWorkflowExecutor getWorkflowExecutor();
    ILyshraOpenAppWorkflowStepExecutor getWorkflowStepExecutor();
    ILyshraOpenAppProcessorExecutor getProcessorExecutor();

    /**
     * Returns the human task processor executor for handling human-in-the-loop workflow steps.
     */
    ILyshraOpenAppHumanTaskProcessorExecutor getHumanTaskProcessorExecutor();

    /**
     * Returns the human task service for creating and managing human tasks.
     */
    ILyshraOpenAppHumanTaskService getHumanTaskService();

    /**
     * Returns the workflow state store manager for persistent state management.
     */
    LyshraOpenAppWorkflowStateStoreManager getWorkflowStateStoreManager();

    /**
     * Returns the active workflow state store for querying and managing workflow states.
     */
    ILyshraOpenAppWorkflowStateStore getWorkflowStateStore();
}
