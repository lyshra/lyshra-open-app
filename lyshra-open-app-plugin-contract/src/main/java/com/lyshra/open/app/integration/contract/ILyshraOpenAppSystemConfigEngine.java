package com.lyshra.open.app.integration.contract;

import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ILyshraOpenAppSystemConfigEngine {
    Mono<Instant> getConfigLastModifiedAt(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    Mono<String> getConfigValue(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    Mono<Boolean> getConfigValueAsBoolean(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    Mono<Integer> getConfigValueAsInteger(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    Mono<Long> getConfigValueAsLong(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    Mono<Double> getConfigValueAsDouble(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
}
