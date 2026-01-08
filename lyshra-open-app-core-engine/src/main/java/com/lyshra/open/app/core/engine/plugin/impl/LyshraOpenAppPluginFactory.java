package com.lyshra.open.app.core.engine.plugin.impl;

import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginFactory;
import com.lyshra.open.app.core.exception.node.LyshraOpenAppProcessorNotFound;
import com.lyshra.open.app.core.exception.node.LyshraOpenAppWorkflowNotFound;
import com.lyshra.open.app.core.exception.node.LyshraOpenAppWorkflowStepNotFound;
import com.lyshra.open.app.core.exception.plugin.LyshraOpenAppPluginAlreadyRegistered;
import com.lyshra.open.app.core.exception.plugin.LyshraOpenAppPluginApisNotFound;
import com.lyshra.open.app.core.exception.plugin.LyshraOpenAppPluginI18NNotFound;
import com.lyshra.open.app.core.exception.plugin.LyshraOpenAppPluginNotFound;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApis;
import com.lyshra.open.app.integration.contract.i18n.ILyshraOpenAppI18nConfig;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessor;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessors;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflow;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflows;
import com.lyshra.open.app.integration.models.LyshraOpenAppPluginIdentifier;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class LyshraOpenAppPluginFactory implements ILyshraOpenAppPluginFactory {
    private final Map<LyshraOpenAppPluginIdentifier, LyshraOpenAppPluginDescriptor> pluginMap = new ConcurrentHashMap<>();
    private LyshraOpenAppPluginFactory() {}

    public static LyshraOpenAppPluginFactory getInstance() {
        return SingletonHelper.INSTANCE;
    }

    private static final class SingletonHelper {
        private static final LyshraOpenAppPluginFactory INSTANCE = new LyshraOpenAppPluginFactory();
    }

    private LyshraOpenAppPluginIdentifier toLyshraOpenAppPluginIdentifier(ILyshraOpenAppPluginIdentifier identifier) {
        if (identifier instanceof LyshraOpenAppPluginIdentifier) {
            return (LyshraOpenAppPluginIdentifier) identifier;
        }
        return new LyshraOpenAppPluginIdentifier(identifier.getOrganization(), identifier.getModule(), identifier.getVersion());
    }

    // using the default package so this is not exposed to the outside world
    void put(ILyshraOpenAppPluginIdentifier identifier, LyshraOpenAppPluginDescriptor descriptor) {
        LyshraOpenAppPluginIdentifier lyshraOpenAppPluginIdentifier = toLyshraOpenAppPluginIdentifier(identifier);
        if (pluginMap.containsKey(lyshraOpenAppPluginIdentifier)) {
            LyshraOpenAppPluginDescriptor existingDescriptor = pluginMap.get(lyshraOpenAppPluginIdentifier);
            throw new LyshraOpenAppPluginAlreadyRegistered(identifier, existingDescriptor, descriptor);
        }
        pluginMap.put(lyshraOpenAppPluginIdentifier, descriptor);
    }

    public Collection<ILyshraOpenAppPlugin> getAllPlugins() {
        return pluginMap.values()
                .stream()
                .map(LyshraOpenAppPluginDescriptor::getPlugin)
                .toList();
    }

    public LyshraOpenAppPluginDescriptor getPluginDescriptor(ILyshraOpenAppPluginIdentifier identifier) {
        LyshraOpenAppPluginIdentifier lyshraOpenAppPluginIdentifier = toLyshraOpenAppPluginIdentifier(identifier);
        LyshraOpenAppPluginDescriptor pluginDescriptor = pluginMap.get(lyshraOpenAppPluginIdentifier);
        if (pluginDescriptor == null) {
            throw new LyshraOpenAppPluginNotFound(lyshraOpenAppPluginIdentifier);
        }
        return pluginDescriptor;
    }

    public ILyshraOpenAppPlugin getPlugin(ILyshraOpenAppPluginIdentifier identifier) {
        return getPluginDescriptor(identifier).getPlugin();
    }

    public Map<String, ILyshraOpenAppProcessor> getAllProcessor(ILyshraOpenAppPluginIdentifier identifier) {
        ILyshraOpenAppPlugin plugin = getPlugin(identifier);
        return Optional.ofNullable(plugin.getProcessors()).map(ILyshraOpenAppProcessors::getProcessors).orElse(Map.of());
    }

    public ILyshraOpenAppProcessor getProcessor(ILyshraOpenAppProcessorIdentifier identifier) {
        ILyshraOpenAppProcessor processor = getAllProcessor(identifier).get(identifier.getProcessorName());
        if (processor == null) {
            throw new LyshraOpenAppProcessorNotFound(identifier);
        }
        return processor;
    }

    public Map<String, ILyshraOpenAppWorkflow> getAllWorkflows(ILyshraOpenAppPluginIdentifier identifier) {
        ILyshraOpenAppPlugin plugin = getPlugin(identifier);
        return Optional.ofNullable(plugin.getWorkflows()).map(ILyshraOpenAppWorkflows::getWorkflows).orElse(Map.of());
    }

    public ILyshraOpenAppWorkflow getWorkflow(ILyshraOpenAppWorkflowIdentifier identifier) {
        ILyshraOpenAppWorkflow workflow = getAllWorkflows(identifier).get(identifier.getWorkflowName());
        if (workflow == null) {
            throw new LyshraOpenAppWorkflowNotFound(identifier);
        }
        return workflow;
    }

    public ILyshraOpenAppWorkflowStep getWorkflowStep(ILyshraOpenAppWorkflowStepIdentifier identifier) {
        ILyshraOpenAppWorkflow workflow = getWorkflow(identifier);
        Map<String, ILyshraOpenAppWorkflowStep> steps = Optional.ofNullable(workflow.getSteps()).orElse(Map.of());
        ILyshraOpenAppWorkflowStep workflowStep = steps.get(identifier.getWorkflowStepName());
        if (workflowStep == null) {
            throw new LyshraOpenAppWorkflowStepNotFound(identifier);
        }
        return workflowStep;
    }

    public ILyshraOpenAppApis getApis(ILyshraOpenAppPluginIdentifier identifier) {
        ILyshraOpenAppApis apis = getPlugin(identifier).getApis();
        if (apis == null) {
            throw new LyshraOpenAppPluginApisNotFound(identifier);
        }
        return apis;
    }

    public ILyshraOpenAppI18nConfig getI18n(ILyshraOpenAppPluginIdentifier identifier) {
        ILyshraOpenAppI18nConfig i18n = getPlugin(identifier).getI18n();
        if (i18n == null) {
            throw new LyshraOpenAppPluginI18NNotFound(identifier);
        }
        return i18n;
    }

}
