package com.lyshra.open.app.core.engine.plugin;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApis;
import com.lyshra.open.app.integration.contract.i18n.ILyshraOpenAppI18nConfig;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessor;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflow;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;

import java.util.Collection;
import java.util.Map;

public interface ILyshraOpenAppPluginFactory {
    Collection<ILyshraOpenAppPlugin> getAllPlugins();
    ILyshraOpenAppPluginDescriptor getPluginDescriptor(ILyshraOpenAppPluginIdentifier identifier);
    ILyshraOpenAppPlugin getPlugin(ILyshraOpenAppPluginIdentifier identifier);
    Map<String, ILyshraOpenAppProcessor> getAllProcessor(ILyshraOpenAppPluginIdentifier identifier);
    ILyshraOpenAppProcessor getProcessor(ILyshraOpenAppProcessorIdentifier identifier);
    Map<String, ILyshraOpenAppWorkflow> getAllWorkflows(ILyshraOpenAppPluginIdentifier identifier);
    ILyshraOpenAppWorkflow getWorkflow(ILyshraOpenAppWorkflowIdentifier identifier);
    ILyshraOpenAppWorkflowStep getWorkflowStep(ILyshraOpenAppWorkflowStepIdentifier identifier);
    ILyshraOpenAppApis getApis(ILyshraOpenAppPluginIdentifier identifier);
    ILyshraOpenAppI18nConfig getI18n(ILyshraOpenAppPluginIdentifier identifier);
}
