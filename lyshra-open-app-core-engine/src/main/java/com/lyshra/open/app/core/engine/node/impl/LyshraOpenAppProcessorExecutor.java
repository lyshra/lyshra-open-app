package com.lyshra.open.app.core.engine.node.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.node.ILyshraOpenAppProcessorExecutor;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginDescriptor;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginFactory;
import com.lyshra.open.app.core.exception.codes.LyshraOpenAppInternalErrorCodes;
import com.lyshra.open.app.core.exception.node.LyshraOpenAppProcessorExecutionException;
import com.lyshra.open.app.core.models.LyshraOpenAppConstraintViolation;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppObjectMapper;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessor;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorFunction;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class LyshraOpenAppProcessorExecutor implements ILyshraOpenAppProcessorExecutor {

    private final ILyshraOpenAppFacade facade;
    private LyshraOpenAppProcessorExecutor() {
        this.facade = LyshraOpenAppFacade.getInstance();
    }

    private static final class SingletonHelper {
        private static final LyshraOpenAppProcessorExecutor INSTANCE = new LyshraOpenAppProcessorExecutor();
    }

    public static ILyshraOpenAppProcessorExecutor getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public Mono<ILyshraOpenAppProcessorResult> execute(
            ILyshraOpenAppProcessorIdentifier identifier,
            Map<String, Object> inputConfig,
            ILyshraOpenAppContext context) throws LyshraOpenAppProcessorExecutionException {

        ILyshraOpenAppProcessor processor = facade.getPluginFactory().getProcessor(identifier);
        return Mono.just(inputConfig)
                .doOnNext(inputConfig0 -> doPreStartActions(identifier, inputConfig0, context))

                // Input config creation and validation
                .flatMap(inputConfig0 -> transformInputConfigForProcessor(identifier, inputConfig0, processor))
                .flatMap(processorInputConfig -> validateInputConstraints(identifier, processorInputConfig))
                .flatMap(processorInputConfig -> validateProcessorInputConfig(identifier, processorInputConfig, processor))

                // Processor execution
                .flatMap(processorInputConfig -> doExecute(identifier, context, processorInputConfig, processor))

                // to handle scenarios where the processor does not return any output and the engine needs to continue execution
                .switchIfEmpty(Mono.just(LyshraOpenAppProcessorOutput.ofDefaultBranch()))

                .doOnNext(res -> doPostEndActions(identifier, res, context))
                .doOnError(throwable -> doPostEndActions(identifier, throwable, context));
    }

    private void doPreStartActions(
            ILyshraOpenAppProcessorIdentifier identifier,
            Map<String, Object> rawInput,
            ILyshraOpenAppContext context) {

        context.captureProcessorStart(identifier, rawInput);
    }

    private Mono<ILyshraOpenAppProcessorInputConfig> transformInputConfigForProcessor(
            ILyshraOpenAppProcessorIdentifier identifier,
            Map<String, Object> rawInputConfig,
            ILyshraOpenAppProcessor processor) {

        try {
            ILyshraOpenAppObjectMapper objectMapper = facade.getObjectMapper();
            Class<? extends ILyshraOpenAppProcessorInputConfig> inputConfigType = processor.getInputConfigType();
            ILyshraOpenAppProcessorInputConfig inputConfig = objectMapper.convertValue(rawInputConfig, inputConfigType);
            return Mono.just(inputConfig);
        } catch (Exception e) {
            return Mono.error(new LyshraOpenAppProcessorExecutionException(
                    identifier,
                    LyshraOpenAppInternalErrorCodes.PROCESSOR_INPUT_TRANSFORMATION_FAILED,
                    e));
        }
    }

    private Mono<? extends ILyshraOpenAppProcessorInputConfig> validateInputConstraints(
            ILyshraOpenAppProcessorIdentifier identifier,
            ILyshraOpenAppProcessorInputConfig processorInputConfig) {

        ILyshraOpenAppPluginFactory lyshraOpenAppPluginFactory = facade.getPluginFactory();
        ILyshraOpenAppPluginDescriptor pluginDescriptor = lyshraOpenAppPluginFactory.getPluginDescriptor(identifier);
        ValidatorFactory validatorFactory = pluginDescriptor.getValidatorFactory();
        Set<ConstraintViolation<Object>> violations;
        try {
            violations = validatorFactory.getValidator().validate(processorInputConfig);
        } catch (Exception e) {
            return Mono.error(new LyshraOpenAppProcessorExecutionException(
                    identifier,
                    LyshraOpenAppInternalErrorCodes.PROCESSOR_INPUT_CONSTRAINT_VIOLATION_EXECUTION_FAILED)
            );
        }

        if (!violations.isEmpty()) {
            List<LyshraOpenAppConstraintViolation> constraintViolations = violations.stream()
                    .map(violation -> {
                        Map<String, String> templateVariables = new LinkedHashMap<>();
                        violation.getConstraintDescriptor()
                                .getAttributes()
                                .forEach((key, value) -> templateVariables.put(key, value.toString()));

                        return new LyshraOpenAppConstraintViolation(
                                violation.getRootBeanClass(),
                                violation.getPropertyPath().toString(),
                                violation.getMessage(),
                                Collections.unmodifiableMap(templateVariables)
                        );
                    })
                    .toList();

            return Mono.error(new LyshraOpenAppProcessorExecutionException(
                    identifier,
                    LyshraOpenAppInternalErrorCodes.PROCESSOR_INPUT_CONSTRAINT_VIOLATION_FAILED,
                    constraintViolations));
        }
        return Mono.just(processorInputConfig);
    }

    private Mono<? extends ILyshraOpenAppProcessorInputConfig> validateProcessorInputConfig(
            ILyshraOpenAppProcessorIdentifier identifier,
            ILyshraOpenAppProcessorInputConfig processorInputConfig,
            ILyshraOpenAppProcessor processor) {

        try {
            processor.getInputConfigValidator().validateInputConfig(processorInputConfig);
        } catch (LyshraOpenAppProcessorRuntimeException e) {
            return Mono.error(new LyshraOpenAppProcessorExecutionException(identifier, e));
        } catch (Exception e) {
            return Mono.error(new LyshraOpenAppProcessorExecutionException(
                    identifier,
                    LyshraOpenAppInternalErrorCodes.PROCESSOR_INPUT_VALIDATION_PLUGIN_INTERNAL_ERROR,
                    e));
        }
        return Mono.just(processorInputConfig);
    }

    private Mono<ILyshraOpenAppProcessorResult> doExecute(
            ILyshraOpenAppProcessorIdentifier identifier,
            ILyshraOpenAppContext context,
            ILyshraOpenAppProcessorInputConfig processorInputConfig,
            ILyshraOpenAppProcessor processor) {

        try {
            ILyshraOpenAppProcessorFunction processorFunction = processor.getProcessorFunction();
            return processorFunction.process(processorInputConfig, context, facade);
        } catch (LyshraOpenAppProcessorRuntimeException e) {
            return Mono.error(new LyshraOpenAppProcessorExecutionException(identifier, e));
        } catch (Exception e) {
            return Mono.error(new LyshraOpenAppProcessorExecutionException(
                    identifier,
                    LyshraOpenAppInternalErrorCodes.PROCESSOR_EXECUTION_FAILED,
                    e));
        }
    }

    private void doPostEndActions(
            ILyshraOpenAppProcessorIdentifier identifier,
            ILyshraOpenAppProcessorResult res,
            ILyshraOpenAppContext context) {

        context.captureProcessorEnd(identifier, res);
    }

    private void doPostEndActions(
            ILyshraOpenAppProcessorIdentifier identifier,
            Throwable throwable,
            ILyshraOpenAppContext context) {

        context.captureProcessorEnd(identifier, throwable);
    }

}

