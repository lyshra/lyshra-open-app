package com.lyshra.open.app.core.processors.plugin.processors.api;

import com.lyshra.open.app.core.processors.plugin.processors.api.auth.ApiRequestContext;
import com.lyshra.open.app.core.processors.plugin.processors.api.auth.AuthHandlerFactory;
import com.lyshra.open.app.core.processors.plugin.processors.api.auth.IAuthHandler;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthProfile;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiEndpoint;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiService;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApis;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorInputConfig;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpMethod;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import com.lyshra.open.app.integration.exception.LyshraOpenAppProcessorRuntimeException;
import com.lyshra.open.app.integration.models.apis.auth.ApiKeyAuthConfig;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorDefinition;
import com.lyshra.open.app.integration.models.processors.LyshraOpenAppProcessorOutput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Processor that executes HTTP API calls using WebClient.
 * Uses the endpoint/service/auth profile model defined in the plugin APIs.
 */
public class ApiProcessor {

    private static final WebClient WEB_CLIENT = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiProcessorInputConfig implements ILyshraOpenAppProcessorInputConfig {

        /**
         * The name of the plugin that defines the API.
         * Format: "groupId/artifactId/version" or just the plugin identifier.
         */
        @NotBlank(message = "{api.processor.input.pluginName.null}")
        @Size(min = 1, max = 200, message = "{api.processor.input.pluginName.invalid.length}")
        private String pluginName;

        /**
         * The name of the endpoint to invoke (as defined in the plugin's API definitions).
         */
        @NotBlank(message = "{api.processor.input.endpointName.null}")
        @Size(min = 1, max = 200, message = "{api.processor.input.endpointName.invalid.length}")
        private String endpointName;

        /**
         * Override path variables (optional).
         * These will be merged with/override the endpoint's default path variables.
         */
        private Map<String, String> pathVariables;

        /**
         * Override query parameters (optional).
         * These will be merged with/override the endpoint's default query params.
         */
        private Map<String, String> queryParams;

        /**
         * Override headers (optional).
         * These will be merged with/override the endpoint's default headers.
         */
        private Map<String, String> headers;

        /**
         * Override request body (optional).
         * If provided, this overrides the endpoint's default body.
         */
        private Object body;

        /**
         * Override timeout in milliseconds (optional).
         * If not provided, uses the endpoint's default timeout.
         */
        private Integer timeoutMs;
    }

    @AllArgsConstructor
    @Getter
    public enum ApiProcessorErrorCodes implements ILyshraOpenAppErrorInfo {
        ENDPOINT_NOT_FOUND(
                "API_001",
                LyshraOpenAppHttpStatus.BAD_REQUEST,
                "Endpoint not found: {endpointName} in plugin: {pluginName}",
                "Please ensure the endpoint name is correct and the plugin defines this endpoint"
        ),
        SERVICE_NOT_FOUND(
                "API_002",
                LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
                "Service not found: {serviceName} referenced by endpoint: {endpointName}",
                "Please ensure the service is defined in the plugin's API definitions"
        ),
        AUTH_PROFILE_NOT_FOUND(
                "API_003",
                LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
                "Auth profile not found: {authProfileName} referenced by service: {serviceName}",
                "Please ensure the auth profile is defined in the plugin's API definitions"
        ),
        API_VALIDATION_FAILED(
                "API_004",
                LyshraOpenAppHttpStatus.BAD_REQUEST,
                "API configuration validation failed: {message}",
                "Please check the API configuration and ensure all required fields are provided"
        ),
        API_EXECUTION_FAILED(
                "API_005",
                LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
                "API execution failed: {message}",
                "Please check the API endpoint and request configuration"
        ),
        PLUGIN_APIS_NOT_FOUND(
                "API_006",
                LyshraOpenAppHttpStatus.BAD_REQUEST,
                "No API definitions found in plugin: {pluginName}",
                "Please ensure the plugin defines API endpoints"
        ),
        AUTH_HANDLER_NOT_FOUND(
                "API_007",
                LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
                "No auth handler found for auth type: {authType}",
                "Please ensure the auth type is supported"
        );

        private final String errorCode;
        private final LyshraOpenAppHttpStatus httpStatus;
        private final String errorTemplate;
        private final String resolutionTemplate;
    }

    public static LyshraOpenAppProcessorDefinition.BuildStep build(
            LyshraOpenAppProcessorDefinition.InitialStepBuilder processorBuilder) {

        return processorBuilder
                .name("API_PROCESSOR")
                .humanReadableNameTemplate("api.processor.name")
                .searchTagsCsvTemplate("api.processor.search.tags")
                .errorCodeEnum(ApiProcessorErrorCodes.class)
                .inputConfigType(ApiProcessorInputConfig.class)
                .sampleInputConfigs(List.of(
                        createSampleConfig()
                ))
                .validateInputConfig(ApiProcessor::validateInputConfig)
                .process((input, context, facade) -> {
                    ApiProcessorInputConfig config = (ApiProcessorInputConfig) input;

                    // Get the plugin's API definitions
                    ILyshraOpenAppApis apis = facade.getPluginApis(config.getPluginName());
                    if (apis == null) {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                ApiProcessorErrorCodes.PLUGIN_APIS_NOT_FOUND,
                                Map.of("pluginName", config.getPluginName())
                        ));
                    }

                    // Get the endpoint
                    ILyshraOpenAppApiEndpoint endpoint = apis.getEndPoints().get(config.getEndpointName());
                    if (endpoint == null) {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                ApiProcessorErrorCodes.ENDPOINT_NOT_FOUND,
                                Map.of(
                                        "endpointName", config.getEndpointName(),
                                        "pluginName", config.getPluginName()
                                )
                        ));
                    }

                    // Get the service
                    ILyshraOpenAppApiService service = apis.getServices().get(endpoint.getServiceName());
                    if (service == null) {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                ApiProcessorErrorCodes.SERVICE_NOT_FOUND,
                                Map.of(
                                        "serviceName", endpoint.getServiceName(),
                                        "endpointName", config.getEndpointName()
                                )
                        ));
                    }

                    // Get the auth profile (if service uses authentication)
                    ILyshraOpenAppApiAuthProfile authProfile = null;
                    if (service.getAuthProfile() != null && !service.getAuthProfile().isBlank()) {
                        authProfile = apis.getAuthProfiles().get(service.getAuthProfile());
                        if (authProfile == null) {
                            return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                    ApiProcessorErrorCodes.AUTH_PROFILE_NOT_FOUND,
                                    Map.of(
                                            "authProfileName", service.getAuthProfile(),
                                            "serviceName", service.getName()
                                    )
                            ));
                        }
                    }

                    // Execute the API call
                    return executeApiCall(config, endpoint, service, authProfile, context, facade)
                            .map(output -> (com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult) output);
                });
    }

    private static Mono<LyshraOpenAppProcessorOutput> executeApiCall(
            ApiProcessorInputConfig config,
            ILyshraOpenAppApiEndpoint endpoint,
            ILyshraOpenAppApiService service,
            ILyshraOpenAppApiAuthProfile authProfile,
            com.lyshra.open.app.integration.contract.ILyshraOpenAppContext context,
            com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade facade) {

        try {
            // Build the URL
            String url = buildUrl(service.getBaseUrl(), endpoint.getPath(),
                    mergePathVariables(endpoint.getPathVariables(), config.getPathVariables()),
                    mergeQueryParams(endpoint.getQueryParams(), config.getQueryParams(), authProfile));

            // Merge headers
            Map<String, String> headers = mergeHeaders(endpoint.getHeaders(), config.getHeaders());

            // Determine the body
            Object body = config.getBody() != null ? config.getBody() : endpoint.getBody();
            String bodyAsString = body != null ? convertBodyToString(body, facade) : "";

            // Determine timeout
            int timeoutMs = config.getTimeoutMs() != null ? config.getTimeoutMs() : endpoint.getTimeoutMs();
            if (timeoutMs <= 0) {
                timeoutMs = 30000; // Default 30 seconds
            }

            // Create request context for auth handler
            ApiRequestContext requestContext = ApiRequestContext.builder()
                    .url(url)
                    .method(endpoint.getMethod())
                    .headers(headers)
                    .body(body)
                    .bodyAsString(bodyAsString)
                    .queryParams(mergeQueryParams(endpoint.getQueryParams(), config.getQueryParams(), null))
                    .contentType(headers.getOrDefault("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                    .timestamp(System.currentTimeMillis())
                    .build();

            // Create the request
            HttpMethod httpMethod = convertMethod(endpoint.getMethod());
            WebClient.RequestBodySpec requestSpec = WEB_CLIENT
                    .method(httpMethod)
                    .uri(url);

            // Apply headers
            WebClient.RequestHeadersSpec<?> headersSpec = requestSpec;
            for (Map.Entry<String, String> header : headers.entrySet()) {
                headersSpec = headersSpec.header(header.getKey(), header.getValue());
            }

            // Apply authentication if configured
            if (authProfile != null && authProfile.getConfig() != null) {
                IAuthHandler authHandler = AuthHandlerFactory.getInstance()
                        .getHandler(authProfile.getType())
                        .orElseThrow(() -> new LyshraOpenAppProcessorRuntimeException(
                                ApiProcessorErrorCodes.AUTH_HANDLER_NOT_FOUND,
                                Map.of("authType", authProfile.getType().name())
                        ));

                // Validate auth config
                authHandler.validateConfig(authProfile.getConfig());

                // Apply auth
                headersSpec = authHandler.applyAuth(headersSpec, authProfile.getConfig(), requestContext);
            }

            // Apply body if present and method supports it
            if (body != null && hasRequestBody(httpMethod)) {
                headersSpec = ((WebClient.RequestBodySpec) headersSpec)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(body));
            }

            // Execute the request
            final int finalTimeout = timeoutMs;
            return headersSpec
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(finalTimeout))
                    .map(response -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", 200);
                        result.put("data", response);
                        return LyshraOpenAppProcessorOutput.ofData(result);
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", ex.getStatusCode().value());
                        result.put("error", ex.getResponseBodyAsString());
                        result.put("headers", ex.getHeaders().toSingleValueMap());
                        return Mono.just(LyshraOpenAppProcessorOutput.ofData(result));
                    })
                    .onErrorResume(Exception.class, ex -> {
                        return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                                ApiProcessorErrorCodes.API_EXECUTION_FAILED,
                                Map.of("message", ex.getMessage())
                        ));
                    });

        } catch (Exception e) {
            return Mono.error(new LyshraOpenAppProcessorRuntimeException(
                    ApiProcessorErrorCodes.API_EXECUTION_FAILED,
                    Map.of("message", e.getMessage())
            ));
        }
    }

    private static String buildUrl(String baseUrl, String path, Map<String, String> pathVariables, Map<String, String> queryParams) {
        // Replace path variables
        String resolvedPath = path;
        if (pathVariables != null) {
            for (Map.Entry<String, String> entry : pathVariables.entrySet()) {
                resolvedPath = resolvedPath.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        // Build URL with query params
        String fullUrl = baseUrl.endsWith("/") ? baseUrl + resolvedPath.substring(1) : baseUrl + resolvedPath;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(fullUrl);

        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }

        return builder.build().toUriString();
    }

    private static Map<String, String> mergePathVariables(Map<String, String> defaults, Map<String, String> overrides) {
        Map<String, String> result = new HashMap<>();
        if (defaults != null) {
            result.putAll(defaults);
        }
        if (overrides != null) {
            result.putAll(overrides);
        }
        return result;
    }

    private static Map<String, String> mergeQueryParams(Map<String, String> defaults, Map<String, String> overrides,
                                                        ILyshraOpenAppApiAuthProfile authProfile) {
        Map<String, String> result = new HashMap<>();
        if (defaults != null) {
            result.putAll(defaults);
        }
        if (overrides != null) {
            result.putAll(overrides);
        }

        // Add API key to query params if configured
        if (authProfile != null && authProfile.getConfig() instanceof ApiKeyAuthConfig apiKeyConfig) {
            if (apiKeyConfig.getAddTo() == ApiKeyAuthConfig.ApiKeyLocation.QUERY) {
                result.put(apiKeyConfig.getKey(), apiKeyConfig.getValue());
            }
        }

        return result;
    }

    private static Map<String, String> mergeHeaders(Map<String, String> defaults, Map<String, String> overrides) {
        Map<String, String> result = new HashMap<>();
        if (defaults != null) {
            result.putAll(defaults);
        }
        if (overrides != null) {
            result.putAll(overrides);
        }
        return result;
    }

    private static HttpMethod convertMethod(LyshraOpenAppHttpMethod method) {
        return switch (method) {
            case GET -> HttpMethod.GET;
            case POST -> HttpMethod.POST;
            case PUT -> HttpMethod.PUT;
            case DELETE -> HttpMethod.DELETE;
            case PATCH -> HttpMethod.PATCH;
            case HEAD -> HttpMethod.HEAD;
            case OPTIONS -> HttpMethod.OPTIONS;
            case TRACE -> HttpMethod.TRACE;
        };
    }

    private static boolean hasRequestBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT ||
                method == HttpMethod.PATCH || method == HttpMethod.DELETE;
    }

    private static String convertBodyToString(Object body, com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade facade) {
        if (body == null) {
            return "";
        }
        if (body instanceof String) {
            return (String) body;
        }
        try {
            // Convert to String using the object mapper's convertValue
            return facade.getObjectMapper().convertValue(body, String.class);
        } catch (Exception e) {
            return body.toString();
        }
    }

    private static void validateInputConfig(ILyshraOpenAppProcessorInputConfig input) {
        // Basic validation is done via annotations
        // Additional validation can be added here if needed
    }

    private static ApiProcessorInputConfig createSampleConfig() {
        ApiProcessorInputConfig config = new ApiProcessorInputConfig();
        config.setPluginName("my-organization/my-module/v1");
        config.setEndpointName("getUser");
        config.setPathVariables(Map.of("userId", "${$data.userId}"));
        config.setQueryParams(Map.of("includeDetails", "true"));
        config.setTimeoutMs(30000);
        return config;
    }
}
