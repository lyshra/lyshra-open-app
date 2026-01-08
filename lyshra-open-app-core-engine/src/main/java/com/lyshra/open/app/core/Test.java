package com.lyshra.open.app.core;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.message.ILyshraOpenAppPluginMessageSource;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppWorkflowExecutor;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginDescriptor;
import com.lyshra.open.app.core.engine.plugin.impl.LyshraOpenAppPluginLoader;
import com.lyshra.open.app.core.exception.codes.LyshraOpenAppInternalErrorCodes;
import com.lyshra.open.app.core.exception.node.LyshraOpenAppWorkflowStepExecutionException;
import com.lyshra.open.app.core.models.LyshraOpenAppContext;
import com.lyshra.open.app.core.models.LyshraOpenAppErrorResponse;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.models.LyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.integration.models.workflows.LyshraOpenAppWorkflowIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class Test {

    private static final ILyshraOpenAppFacade facade = LyshraOpenAppFacade.getInstance();
    private static final ILyshraOpenAppContext context = createLyshraOpenAppContext();

    public static void main(String[] args) {
        String pluginsRootDir = "lyshra-open-app-plugins";
        LyshraOpenAppPluginLoader.getInstance().loadAllPlugins(Paths.get(pluginsRootDir));
        ILyshraOpenAppPluginIdentifier identifier = new LyshraOpenAppPluginIdentifier("my-organization", "my-module", "v1");
        ILyshraOpenAppWorkflowIdentifier workflowIdentifier = new LyshraOpenAppWorkflowIdentifier(identifier, "workflow1");
        ILyshraOpenAppWorkflowExecutor workflowExecutor = facade.getWorkflowExecutor();
        try {
            workflowExecutor.execute(workflowIdentifier, context).block();
        } catch (Exception e) {
            if (e instanceof LyshraOpenAppWorkflowStepExecutionException stepExecutionException) {
                LyshraOpenAppErrorResponse lyshraOpenAppErrorResponse = handleError(stepExecutionException);
                log.error("lyshraOpenAppErrorResponse: [{}]", lyshraOpenAppErrorResponse);
            }
        }
        log.info("Done!");
    }

    private static LyshraOpenAppErrorResponse handleError(LyshraOpenAppWorkflowStepExecutionException stepExecutionException) {
        ILyshraOpenAppErrorInfo errorInfo = stepExecutionException.getErrorInfo();

        ILyshraOpenAppPluginMessageSource messageSource;
        if (errorInfo instanceof LyshraOpenAppInternalErrorCodes) {
            messageSource = facade.getCoreEngineMessageSource();
        } else {
            ILyshraOpenAppProcessorIdentifier processorIdentifier = stepExecutionException.getProcessorIdentifier();
            ILyshraOpenAppPluginDescriptor pluginDescriptor = facade.getPluginFactory().getPluginDescriptor(processorIdentifier);
            messageSource = pluginDescriptor.getMessageSource();
        }

        Locale locale = Locale.getDefault();
        Map<String, String> templateVariables = stepExecutionException.getTemplateVariables();

        return LyshraOpenAppErrorResponse
                .builder()
                .requestId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .workflowStepIdentifier(stepExecutionException.getWorkflowStepIdentifier())
                .processorIdentifier(stepExecutionException.getProcessorIdentifier())
                .errorCode(errorInfo.getErrorCode())
                .httpStatus(errorInfo.getHttpStatus())
                .errorMessage(messageSource.getMessage(errorInfo.getErrorTemplate(), locale, templateVariables))
                .resolutionMessage(messageSource.getMessage(errorInfo.getResolutionTemplate(), locale, templateVariables))
                .stackTrace(Optional.ofNullable(stepExecutionException.getRootCause()).map(ExceptionUtils::getStackTrace).orElse(null))
                .additionalInfo(stepExecutionException.getAdditionalInfo())
                .build();
    }

    private static LyshraOpenAppContext createLyshraOpenAppContext() {
        LyshraOpenAppContext lyshraOpenAppContext = new LyshraOpenAppContext(Map.of("configKey1", "configValue1"));
        lyshraOpenAppContext.getData().put("field1", "hello world");
        lyshraOpenAppContext.getData().put("field2", "hello soni");
        lyshraOpenAppContext.getData().put("field3", "hello universe");
        return lyshraOpenAppContext;
    }

}