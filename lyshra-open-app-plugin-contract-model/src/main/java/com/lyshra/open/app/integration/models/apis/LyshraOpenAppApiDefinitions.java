package com.lyshra.open.app.integration.models.apis;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiAuthProfile;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiEndpoint;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApiService;
import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApis;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Data
public class LyshraOpenAppApiDefinitions implements ILyshraOpenAppApis, Serializable {
    private final Map<String, ILyshraOpenAppApiAuthProfile> authProfiles;
    private final Map<String, ILyshraOpenAppApiService> services;
    private final Map<String, ILyshraOpenAppApiEndpoint> endPoints;

    // ---------------------------------------------------------
    // Entry
    // ---------------------------------------------------------
    public static InitialStepBuilder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------
    // Step Interfaces
    // ---------------------------------------------------------

    public interface InitialStepBuilder {
        ServicesStep authProfiles(Function<AuthProfilesBuilder, AuthProfilesBuilder> f);
    }

    public interface ServicesStep {
        EndpointsStep services(Function<ServicesBuilder, ServicesBuilder> f);
    }

    public interface EndpointsStep {
        BuildStep endPoints(Function<EndpointsBuilder, EndpointsBuilder> f);
    }

    public interface BuildStep {
        LyshraOpenAppApiDefinitions build();
    }


    // ---------------------------------------------------------
    // Collection Builders
    // ---------------------------------------------------------

    public static class AuthProfilesBuilder {
        private final Map<String, ILyshraOpenAppApiAuthProfile> map = new LinkedHashMap<>();

        public AuthProfilesBuilder authProfile(
                Function<LyshraOpenAppApiAuthProfile.InitialStepBuilder, LyshraOpenAppApiAuthProfile.BuildStep> fn
        ) {
            LyshraOpenAppApiAuthProfile profile =
                    fn.apply(LyshraOpenAppApiAuthProfile.builder()).build();
            map.put(profile.getName(), profile);
            return this;
        }

        public AuthProfilesBuilder authProfile(ILyshraOpenAppApiAuthProfile profile) {
            map.put(profile.getName(), profile);
            return this;
        }

        Map<String, ILyshraOpenAppApiAuthProfile> build() {
            return map;
        }
    }


    public static class ServicesBuilder {
        private final Map<String, ILyshraOpenAppApiService> map = new LinkedHashMap<>();

        public ServicesBuilder service(
                Function<LyshraOpenAppApiService.InitialStepBuilder, LyshraOpenAppApiService.BuildStep> fn
        ) {
            LyshraOpenAppApiService svc =
                    fn.apply(LyshraOpenAppApiService.builder()).build();
            map.put(svc.getName(), svc);
            return this;
        }

        public ServicesBuilder service(ILyshraOpenAppApiService svc) {
            map.put(svc.getName(), svc);
            return this;
        }

        Map<String, ILyshraOpenAppApiService> build() {
            return map;
        }
    }


    public static class EndpointsBuilder {
        private final Map<String, ILyshraOpenAppApiEndpoint> map = new LinkedHashMap<>();

        public EndpointsBuilder endPoint(
                Function<LyshraOpenAppApiEndpoint.InitialStepBuilder, LyshraOpenAppApiEndpoint.BuildStep> fn
        ) {
            LyshraOpenAppApiEndpoint ep =
                    fn.apply(LyshraOpenAppApiEndpoint.builder()).build();
            map.put(ep.getName(), ep);
            return this;
        }

        public EndpointsBuilder endPoint(ILyshraOpenAppApiEndpoint ep) {
            map.put(ep.getName(), ep);
            return this;
        }

        Map<String, ILyshraOpenAppApiEndpoint> build() {
            return map;
        }
    }


    // ---------------------------------------------------------
    // Main Builder Implementation
    // ---------------------------------------------------------

    private static class Builder implements
            InitialStepBuilder,
            ServicesStep,
            EndpointsStep,
            BuildStep {

        private Map<String, ILyshraOpenAppApiAuthProfile> authProfiles;
        private Map<String, ILyshraOpenAppApiService> services;
        private Map<String, ILyshraOpenAppApiEndpoint> endPoints;

        @Override
        public ServicesStep authProfiles(Function<AuthProfilesBuilder, AuthProfilesBuilder> f) {
            AuthProfilesBuilder apb = f.apply(new AuthProfilesBuilder());
            this.authProfiles = apb.build();
            return this;
        }

        @Override
        public EndpointsStep services(Function<ServicesBuilder, ServicesBuilder> f) {
            ServicesBuilder sb = f.apply(new ServicesBuilder());
            this.services = sb.build();
            return this;
        }

        @Override
        public BuildStep endPoints(Function<EndpointsBuilder, EndpointsBuilder> f) {
            EndpointsBuilder eb = f.apply(new EndpointsBuilder());
            this.endPoints = eb.build();
            return this;
        }

        @Override
        public LyshraOpenAppApiDefinitions build() {
            return new LyshraOpenAppApiDefinitions(
                    Collections.unmodifiableMap(authProfiles),
                    Collections.unmodifiableMap(services),
                    Collections.unmodifiableMap(endPoints)
            );
        }
    }
}