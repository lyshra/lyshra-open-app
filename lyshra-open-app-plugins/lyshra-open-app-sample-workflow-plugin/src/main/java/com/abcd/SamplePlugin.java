package com.abcd;

import com.lyshra.open.app.integration.ILyshraOpenAppPluginProvider;
import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIO;
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
    class SampleProcessorIO<T> implements ILyshraOpenAppProcessorIO {
        T data;
    }

    @Override
    public LyshraOpenAppPlugin create(ILyshraOpenAppPluginFacade lyshraOpenAppFacade) {
        return LyshraOpenAppPlugin
                .builder()
                .identifier(new LyshraOpenAppPluginIdentifier("my-organization", "my-module", "v1"))
                .documentationResourcePath("documentation.md")
                .apis(this::apiBuilder)
                .workflows(workflowsBuilder -> workflowsBuilder
                        .workflow(workflowBuilder -> workflowBuilder
                                .name("workflow1")
                                .startStep("step1")
                                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                                .steps(stepsBuilder -> stepsBuilder
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

    private static LyshraOpenAppWorkflowStep.BuildStep step1Builder(LyshraOpenAppWorkflowStep.InitialStepBuilder stepBuilder) {
        LyshraOpenAppPluginIdentifier corePluginIdentifier = new LyshraOpenAppPluginIdentifier("com.lyshra.open.app", "core", "1.0.0");
        return stepBuilder
                .name("step1")
                .description("This is step 1")
                .contextRetention(LyshraOpenAppWorkflowContextRetention.FULL)
                .processor(new LyshraOpenAppProcessorIdentifier(corePluginIdentifier, "IF_PROCESSOR"))
                .inputConfig(Map.of("expression", "a"))
                .timeout(Duration.ofMillis(1000))
                .next(nextBuilder -> nextBuilder
                        .booleanBranch("step2", "step3")
                )
                .onError(onErrorBuilder -> onErrorBuilder
                        .defaultErrorConfig(cfg -> cfg
//                                .retryPolicy(rb -> rb.maxAttempts(3).initialIntervalMs(100).multiplier(1.2).maxIntervalMs(1000))
                                .abortOnError()
//                                .fallbackOnError("step2")
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
                                .retryPolicy(rb -> rb.maxAttempts(3).initialIntervalMs(100).multiplier(1.2).maxIntervalMs(1000))
                                .abortOnError()
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
                                .retryPolicy(rb -> rb.maxAttempts(3).initialIntervalMs(100).multiplier(1.2).maxIntervalMs(1000))
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
