package com.lyshra.open.app.integration.models.apis;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppRetryPolicy;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiEndpoint;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiResponseStatusIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIO;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpMethod;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppRetryPolicy;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

@Data
public class LyshraOpenAppApiEndpoint implements ILyshraOpenAppApiEndpoint, Serializable {
    private final String name;
    private final String description;
    private final String serviceName;
    private final LyshraOpenAppHttpMethod method;
    private final String path;
    private final Map<String, String> headers;
    private final Map<String, String> pathVariables;
    private final Map<String, String> queryParams;
    private final Object body;
    private final int timeoutMs;
    private final Class<? extends ILyshraOpenAppProcessorIO> outputType;
    private final ILyshraOpenAppApiResponseStatusIdentifier responseStatusIdentifier;
    private final ILyshraOpenAppRetryPolicy retryPolicy;

    public static InitialStepBuilder builder() {
        return new LyshraOpenAppApiBuilder();
    }

    public interface InitialStepBuilder { EndpointDescriptionBuilder name(String n); }
    public interface EndpointDescriptionBuilder { EndpointServiceNameBuilder description(String d); }
    public interface EndpointServiceNameBuilder { EndpointMethodBuilder serviceName(String s); }
    public interface EndpointMethodBuilder { EndpointPathBuilder method(LyshraOpenAppHttpMethod m); }
    public interface EndpointPathBuilder { EndpointPathVariablesBuilder path(String path); }
    public interface EndpointPathVariablesBuilder { EndpointQueryParamsBuilder pathVariables(Map<String, String> pathVariables); }
    public interface EndpointQueryParamsBuilder { EndpointHeadersBuilder queryParams(Map<String, String> q); }
    public interface EndpointHeadersBuilder { EndpointBodyBuilder headers(Map<String, String> h); }
    public interface EndpointBodyBuilder { EndpointTimeoutMsBuilder body(Object b); }
    public interface EndpointTimeoutMsBuilder { EndpointOutputTypeBuilder timeoutMs(int t); }
    public interface EndpointOutputTypeBuilder { EndpointResponseIdentifierBuilder outputType(Class<? extends ILyshraOpenAppProcessorIO> o); }
    public interface EndpointResponseIdentifierBuilder {
        EndpointRetryPolicyBuilder responseStatusIdentifier(LyshraOpenAppApiResponseStatusIdentifier r);
        EndpointRetryPolicyBuilder responseStatusIdentifier(Function<LyshraOpenAppApiResponseStatusIdentifier.InitialStepBuilder, LyshraOpenAppApiResponseStatusIdentifier.BuildStep> builder);
    }
    public interface EndpointRetryPolicyBuilder {
        BuildStep retryPolicy(LyshraOpenAppRetryPolicy p);
        BuildStep retryPolicy(Function<LyshraOpenAppRetryPolicy.InitialStepBuilder, LyshraOpenAppRetryPolicy.BuildStep> builder);
    }
    public interface BuildStep { LyshraOpenAppApiEndpoint build(); }

    private static class LyshraOpenAppApiBuilder implements
            InitialStepBuilder,
            EndpointDescriptionBuilder,
            EndpointServiceNameBuilder,
            EndpointMethodBuilder,
            EndpointPathBuilder,
            EndpointPathVariablesBuilder,
            EndpointQueryParamsBuilder,
            EndpointHeadersBuilder,
            EndpointBodyBuilder,
            EndpointTimeoutMsBuilder,
            EndpointOutputTypeBuilder,
            EndpointResponseIdentifierBuilder,
            EndpointRetryPolicyBuilder,
            BuildStep
    {
        private String name;
        private String description;
        private String serviceName;
        private LyshraOpenAppHttpMethod method;
        private String path;
        private Map<String, String> pathVariables;
        private Map<String, String> queryParams;
        private Map<String, String> headers;
        private Object body;
        private int timeoutMs;
        private Class<? extends ILyshraOpenAppProcessorIO> outputType;
        private LyshraOpenAppApiResponseStatusIdentifier responseStatusIdentifier;
        private LyshraOpenAppRetryPolicy retryPolicy;

        public EndpointDescriptionBuilder name(String name) {
            this.name = name;
            return this;
        }

        public EndpointServiceNameBuilder description(String description) {
            this.description = description;
            return this;
        }

        public EndpointMethodBuilder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public EndpointPathBuilder method(LyshraOpenAppHttpMethod method) {
            this.method = method;
            return this;
        }

        public EndpointPathVariablesBuilder path(String path) {
            this.path = path;
            return this;
        }

        public EndpointQueryParamsBuilder pathVariables(Map<String, String> pathVariables) {
            this.pathVariables = pathVariables;
            return this;
        }

        public EndpointHeadersBuilder queryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public EndpointBodyBuilder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public EndpointTimeoutMsBuilder body(Object body) {
            this.body = body;
            return this;
        }

        public EndpointOutputTypeBuilder timeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public EndpointResponseIdentifierBuilder outputType(Class<? extends ILyshraOpenAppProcessorIO> outputType) {
            this.outputType = outputType;
            return this;
        }

        public EndpointRetryPolicyBuilder responseStatusIdentifier(LyshraOpenAppApiResponseStatusIdentifier responseStatusIdentifier) {
            this.responseStatusIdentifier = responseStatusIdentifier;
            return this;
        }

        @Override
        public EndpointRetryPolicyBuilder responseStatusIdentifier(Function<LyshraOpenAppApiResponseStatusIdentifier.InitialStepBuilder, LyshraOpenAppApiResponseStatusIdentifier.BuildStep> builder) {
            this.responseStatusIdentifier = builder.apply(LyshraOpenAppApiResponseStatusIdentifier.builder()).build();
            return this;
        }

        public BuildStep retryPolicy(LyshraOpenAppRetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        @Override
        public BuildStep retryPolicy(Function<LyshraOpenAppRetryPolicy.InitialStepBuilder, LyshraOpenAppRetryPolicy.BuildStep> builder) {
            this.retryPolicy = builder.apply(LyshraOpenAppRetryPolicy.builder()).build();
            return this;
        }

        @Override
        public LyshraOpenAppApiEndpoint build() {
            return new LyshraOpenAppApiEndpoint(
                    name,
                    description,
                    serviceName,
                    method,
                    path,
                    Collections.unmodifiableMap(headers),
                    Collections.unmodifiableMap(pathVariables),
                    Collections.unmodifiableMap(queryParams),
                    body,
                    timeoutMs,
                    outputType,
                    responseStatusIdentifier,
                    retryPolicy
            );
        }

    }

}
