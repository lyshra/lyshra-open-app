package com.abcd;

import com.lyshra.open.app.integration.ILyshraOpenAppPluginProvider;
import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppApiAuthType;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpMethod;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowContextRetention;
import com.lyshra.open.app.integration.models.LyshraOpenAppPlugin;
import com.lyshra.open.app.integration.models.LyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.integration.models.apis.LyshraOpenAppApiAuthProfile;
import com.lyshra.open.app.integration.models.apis.LyshraOpenAppApiDefinitions;
import com.lyshra.open.app.integration.models.apis.LyshraOpenAppApiService;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.models.workflows.step.LyshraOpenAppWorkflowStep;
import com.lyshra.open.app.integration.models.workflows.step.LyshraOpenAppWorkflowStepNext;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Duration;
import java.util.Map;

public class SamplePlugin implements ILyshraOpenAppPluginProvider {

    @Data
    @AllArgsConstructor
    static
    class SampleProcessorIO<T> {
        T data;
    }

    private static LyshraOpenAppPluginIdentifier getPluginIdentifier() {
        return new LyshraOpenAppPluginIdentifier("my-organization", "my-module", "v1");
    }

    @Override
    public LyshraOpenAppPlugin create(ILyshraOpenAppPluginFacade lyshraOpenAppFacade) {
        return LyshraOpenAppPlugin
                .builder()
                .identifier(getPluginIdentifier())
                .documentationResourcePath("documentation.md")
                .apis(this::apiBuilder)
                .workflows(workflowsBuilder -> workflowsBuilder
                        .workflow(workflowBuilder -> workflowBuilder
                                .name("workflow1")
                                .startStep("step0")
                                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                                .steps(stepsBuilder -> stepsBuilder
                                        .step(SamplePlugin::step0Builder)
                                        .step(SamplePlugin::step4Builder)
                                        .step(SamplePlugin::step5Builder)
                                        .step(SamplePlugin::step6Builder)
                                        .step(SamplePlugin::step1Builder)
                                        .step(SamplePlugin::step2Builder)
                                        .step(SamplePlugin::step3Builder)
                                )
                        )
                )
                .i18n(builder -> builder
                        .resourceBundleBasenamePath("i18n/messages")
                )
                .build();
    }

    private static LyshraOpenAppWorkflowStep.BuildStep step0Builder(LyshraOpenAppWorkflowStep.InitialStepBuilder stepBuilder) {
        LyshraOpenAppPluginIdentifier corePluginIdentifier = new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "core", "1.0.0");
        return stepBuilder
                .name("step0")
                .description("JavaScript processor to map input to output#1")
                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                .processor(new LyshraOpenAppProcessorIdentifier(corePluginIdentifier, "JS_PROCESSOR"))
                .inputConfig(Map.of("expression", 
                    "$data = {" +
                    "  'greeting': $data.hello," +
                    "  'total': $data.count," +
                    "  'data': $data.nested," +
                    "  'numbers': $data.list," +
                    "  'mapping': $data.map," +
                    "  'items': $data.complex" +
                    "}"))
                .timeout(Duration.ofMillis(1000))
                .next(nextBuilder -> nextBuilder
                        .defaultBranch("step4")
                )
                .onError(onErrorBuilder -> onErrorBuilder
                        .defaultErrorConfig(cfg -> cfg
                                .fallbackOnError("step4")
                        )
                        .timeoutErrorConfig(cfg -> cfg
                                .retryPolicy(rb -> rb.maxAttempts(3).initialIntervalMs(100).multiplier(1.2).maxIntervalMs(1000))
                                .fallbackOnError("step4")
                        )
                );
    }

    private static LyshraOpenAppWorkflowStep.BuildStep step4Builder(LyshraOpenAppWorkflowStep.InitialStepBuilder stepBuilder) {
        LyshraOpenAppPluginIdentifier corePluginIdentifier = new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "core", "1.0.0");
        return stepBuilder
                .name("step4")
                .description("IF processor for conditional branch")
                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                .processor(new LyshraOpenAppProcessorIdentifier(corePluginIdentifier, "IF_PROCESSOR"))
                .inputConfig(Map.of("expression", "result = $data.total > 5"))
                .noTimeout()
//                .timeout(Duration.ofMillis(1000))
                .next(nextBuilder -> nextBuilder
                        .booleanBranch("step5", "step6")
                )
                .onError(onErrorBuilder -> onErrorBuilder
                        .defaultErrorConfig(cfg -> cfg
                                .fallbackOnError("step6")
                        )
                        .timeoutErrorConfig(cfg -> cfg
                                .retryPolicy(rb -> rb.maxAttempts(3).initialIntervalMs(100).multiplier(1.2).maxIntervalMs(1000))
                                .fallbackOnError("step6")
                        )
                );
    }

    private static LyshraOpenAppWorkflowStep.BuildStep step5Builder(LyshraOpenAppWorkflowStep.InitialStepBuilder stepBuilder) {
        LyshraOpenAppPluginIdentifier corePluginIdentifier = new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "core", "1.0.0");
        return stepBuilder
                .name("step5")
                .description("JavaScript processor to map output#1 to output#2 (true branch)")
                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                .processor(new LyshraOpenAppProcessorIdentifier(corePluginIdentifier, "JS_PROCESSOR"))
                .inputConfig(Map.of("expression", 
                    "$data = {" +
                    "  'transformed': true," +
                    "  'greeting': $data.greeting," +
                    "  'total': $data.total," +
                    "  'numbersCount': $data.numbers ? $data.numbers.length : 0," +
                    "  'itemsCount': $data.items ? $data.items.length : 0" +
                    "}"))
                .noTimeout()
//                .timeout(Duration.ofMillis(1000))
                .next(LyshraOpenAppWorkflowStepNext.InitialStepBuilder::terminate)
                .onError(onErrorBuilder -> onErrorBuilder
                        .defaultErrorConfig(cfg -> cfg
                                .endWorkflow()
                        )
                        .timeoutErrorConfig(cfg -> cfg
                                .retryPolicy(null)
                                .abortOnError()
                        )
                );
    }

    private static LyshraOpenAppWorkflowStep.BuildStep step6Builder(LyshraOpenAppWorkflowStep.InitialStepBuilder stepBuilder) {
        LyshraOpenAppPluginIdentifier corePluginIdentifier = new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "core", "1.0.0");
        return stepBuilder
                .name("step6")
                .description("JavaScript processor to map output#1 to output#3 (false branch)")
                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                .processor(new LyshraOpenAppProcessorIdentifier(corePluginIdentifier, "JS_PROCESSOR"))
                .inputConfig(Map.of("expression", 
                    "$data = {" +
                    "  'transformed': false," +
                    "  'greeting': $data.greeting," +
                    "  'total': $data.total," +
                    "  'dataField': $data.data ? $data.data.field : null," +
                    "  'mappingKey': $data.mapping ? Object.keys($data.mapping)[0] : null" +
                    "}"))
                .noTimeout()
//                .timeout(Duration.ofMillis(1000))
                .next(LyshraOpenAppWorkflowStepNext.InitialStepBuilder::terminate)
                .onError(onErrorBuilder -> onErrorBuilder
                        .defaultErrorConfig(cfg -> cfg
                                .endWorkflow()
                        )
                        .timeoutErrorConfig(cfg -> cfg
                                .retryPolicy(null)
                                .abortOnError()
                        )
                );
    }

    private static LyshraOpenAppWorkflowStep.BuildStep step1Builder(LyshraOpenAppWorkflowStep.InitialStepBuilder stepBuilder) {
        LyshraOpenAppPluginIdentifier corePluginIdentifier = new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "core", "1.0.0");
        return stepBuilder
                .name("step1")
                .description("This is step 1")
                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                .processor(new LyshraOpenAppProcessorIdentifier(corePluginIdentifier, "IF_PROCESSOR"))
                .inputConfig(Map.of("expression", "$data.field1 == 'hello world'"))
                .timeout(Duration.ofMillis(1000))
                .next(nextBuilder -> nextBuilder
                        .booleanBranch("step2", "step3")
                )
                .onError(onErrorBuilder -> onErrorBuilder
                        .defaultErrorConfig(cfg -> cfg
//                                .retryPolicy(rb -> rb.maxAttempts(3).initialIntervalMs(100).multiplier(1.2).maxIntervalMs(1000))
//                                .abortOnError()
                                .fallbackOnError("step2")
                        )
                        .timeoutErrorConfig(cfg -> cfg
                                .retryPolicy(rb -> rb.maxAttempts(3).initialIntervalMs(100).multiplier(1.2).maxIntervalMs(1000))
                                .fallbackOnError("step2")
                        )
                        .customErrorConfigs("", cfg -> cfg
                                .retryPolicy(null)
                                .endWorkflow()
                        )
                );
    }

    private static LyshraOpenAppWorkflowStep.BuildStep step2Builder(LyshraOpenAppWorkflowStep.InitialStepBuilder stepBuilder) {
        LyshraOpenAppPluginIdentifier corePluginIdentifier = new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "core", "1.0.0");
        return stepBuilder
                .name("step2")
                .description("This is step 2")
                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                .processor(new LyshraOpenAppProcessorIdentifier(corePluginIdentifier, "IF_PROCESSOR"))
                .inputConfig(Map.of("expression", "$data.field2 == 'hello soni'"))
                .timeout(Duration.ofMillis(1000))
                .next(nextBuilder -> nextBuilder
                        .booleanBranch(LyshraOpenAppConstants.NOOP_PROCESSOR, "step3")
                )
                .onError(onErrorBuilder -> onErrorBuilder
                        .defaultErrorConfig(cfg -> cfg
//                                .retryPolicy(rb -> rb.maxAttempts(3).initialIntervalMs(100).multiplier(1.2).maxIntervalMs(1000))
//                                .abortOnError()
                                        .fallbackOnError("step3")
                        )
                        .timeoutErrorConfig(cfg -> cfg
                                .retryPolicy(null)
                                .abortOnError()
                        )
                        .customErrorConfigs("", cfg -> cfg
                                .retryPolicy(null)
                                .endWorkflow()
                        )
                );
    }

    private static LyshraOpenAppWorkflowStep.BuildStep step3Builder(LyshraOpenAppWorkflowStep.InitialStepBuilder stepBuilder) {
        LyshraOpenAppPluginIdentifier corePluginIdentifier = new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "core", "1.0.0");
        return stepBuilder
                .name("step3")
                .description("This is step 3")
                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                .processor(new LyshraOpenAppProcessorIdentifier(corePluginIdentifier, "IF_PROCESSOR"))
                .inputConfig(Map.of("expression", "$data.field3 == 'hello universe'"))
                .timeout(Duration.ofMillis(1000))
                .next(LyshraOpenAppWorkflowStepNext.InitialStepBuilder::terminate)
                .onError(onErrorBuilder -> onErrorBuilder
                        .defaultErrorConfig(cfg -> cfg
//                                .retryPolicy(rb -> rb.maxAttempts(3).initialIntervalMs(100).multiplier(1.2).maxIntervalMs(1000))
                                .endWorkflow()
                        )
                        .timeoutErrorConfig(cfg -> cfg
                                .retryPolicy(null)
                                .abortOnError()
                        )
                        .customErrorConfigs("", cfg -> cfg
                                .retryPolicy(null)
                                .endWorkflow()
                        )
                );
    }

    private LyshraOpenAppApiDefinitions.BuildStep apiBuilder(LyshraOpenAppApiDefinitions.InitialStepBuilder builder) {
        return builder
                .authProfiles(authProfilesBuilder -> authProfilesBuilder
                        .authProfile(authProfileBuilder -> authProfileBuilder
                                .name("authProfile1")
                                .type(LyshraOpenAppApiAuthType.NO_AUTH)
                        )
                        .authProfile(authProfileBuilder -> authProfileBuilder
                                .name("authProfile2")
                                .type(LyshraOpenAppApiAuthType.BASIC_AUTH)
                        )
                        .authProfile(authProfileBuilder -> authProfileBuilder
                                .name("authProfile3")
                                .type(LyshraOpenAppApiAuthType.BASIC_AUTH)
                        )
                        .authProfile(LyshraOpenAppApiAuthProfile.builder().name("").type(null).build())
                        .authProfile(authProfileBuilder -> authProfileBuilder
                                .name("authProfile3")
                                .type(LyshraOpenAppApiAuthType.BASIC_AUTH)
                        )
                )
                .services(servicesBuilder -> servicesBuilder
                        .service(serviceBuilder -> serviceBuilder
                                .name("service1")
                                .baseUrl("https://www.service1.com")
                                .authProfile("authProfile1") // refer name
                        )
                        .service(serviceBuilder -> serviceBuilder
                                .name("service1")
                                .baseUrl("https://www.service1.com")
                                .authProfile("authProfile1") // refer name
                        )
                        .service(LyshraOpenAppApiService.builder().name("").baseUrl("").authProfile("").build())
                        .service(serviceBuilder -> serviceBuilder
                                .name("service1")
                                .baseUrl("https://www.service1.com")
                                .authProfile("authProfile1") // refer name
                        )
                )
                .endPoints(endPointsBuilder -> endPointsBuilder
                        .endPoint(endPointBuilder -> endPointBuilder
                                .name("endpoint1")
                                .description("This is endpoint 1")
                                .serviceName("service1")
                                .method(LyshraOpenAppHttpMethod.GET)
                                .path("/path/xyz")
                                .pathVariables(Map.of())
                                .queryParams(Map.of())
                                .headers(Map.of())
                                .body("")
                                .timeoutMs(1)
                                .outputType(SampleProcessorIO.class)
                                .responseStatusIdentifier(b1 -> b1
                                        .success(new LyshraOpenAppExpression(LyshraOpenAppExpressionType.SPEL, "#root.status == 200"))
                                        .failure(new LyshraOpenAppExpression(LyshraOpenAppExpressionType.SPEL, "#root.status >= 400 && #root.status < 500"))
                                        .retry(new LyshraOpenAppExpression(LyshraOpenAppExpressionType.SPEL, "#root.status >= 500"))
                                )
                                .retryPolicy(rpb -> rpb
                                        .maxAttempts(3)
                                        .initialIntervalMs(1000)
                                        .multiplier(2.0)
                                        .maxIntervalMs(10000)
                                )
                        )
                );
    }

}
