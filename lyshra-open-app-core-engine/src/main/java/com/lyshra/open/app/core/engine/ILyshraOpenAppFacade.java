package com.lyshra.open.app.core.engine;

import com.lyshra.open.app.core.engine.database.ILyshraOpenAppDatasourceEngine;
import com.lyshra.open.app.core.engine.message.ILyshraOpenAppPluginMessageSource;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppProcessorExecutor;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowExecutor;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowStepExecutor;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginFactory;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginLoader;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;

public interface ILyshraOpenAppFacade extends ILyshraOpenAppPluginFacade {
    ILyshraOpenAppDatasourceEngine getDatasourceEngine();
    ILyshraOpenAppPluginLoader getPluginLoader();
    ILyshraOpenAppPluginFactory getPluginFactory();
    ILyshraOpenAppPluginMessageSource getCoreEngineMessageSource();
    ILyshraOpenAppWorkflowExecutor getWorkflowExecutor();
    ILyshraOpenAppWorkflowStepExecutor getWorkflowStepExecutor();
    ILyshraOpenAppProcessorExecutor getProcessorExecutor();
}
