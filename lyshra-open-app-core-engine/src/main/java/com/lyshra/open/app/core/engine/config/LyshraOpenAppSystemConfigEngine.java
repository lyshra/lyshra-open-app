package com.lyshra.open.app.core.engine.config;

import com.lyshra.open.app.core.engine.config.evaluator.LyshraOpenAppSystemConfigEvaluatorFactory;
import com.lyshra.open.app.core.engine.config.models.LyshraOpenAppSystemConfig;
import com.lyshra.open.app.core.exception.misc.LyshraOpenAppSystemConfigNotFound;
import com.lyshra.open.app.core.util.CastUtil;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppSystemConfigEngine;
import reactor.core.publisher.Mono;

import java.time.Instant;

public class LyshraOpenAppSystemConfigEngine implements ILyshraOpenAppSystemConfigEngine {

    private final LyshraOpenAppSystemConfigEvaluatorFactory evaluatorFactory;

    private LyshraOpenAppSystemConfigEngine() {
        this.evaluatorFactory = LyshraOpenAppSystemConfigEvaluatorFactory.getInstance();
    }

    private static final class SingletonHelper {
        private static final LyshraOpenAppSystemConfigEngine INSTANCE = new LyshraOpenAppSystemConfigEngine();
    }

    public static ILyshraOpenAppSystemConfigEngine getInstance() {
        return SingletonHelper.INSTANCE;
    }

    private Mono<LyshraOpenAppSystemConfig> loadFromDB(String settingsKey) {
        return Mono.empty();
    }

    public Mono<LyshraOpenAppSystemConfig> getConfig(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return loadFromDB(settingsKey).switchIfEmpty(Mono.error(new LyshraOpenAppSystemConfigNotFound(settingsKey)));
    }

    @Override
    public Mono<Instant> getConfigLastModifiedAt(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getConfig(settingsKey, context, facade)
                .map(LyshraOpenAppSystemConfig::getLastModifiedAt);
    }

    @Override
    public Mono<String> getConfigValue(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getConfig(settingsKey, context, facade)
                .map(config -> evaluatorFactory
                        .get(config.getMode())
                        .evaluate(config, context)
                );
    }

    @Override
    public Mono<Boolean> getConfigValueAsBoolean(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getConfigValue(settingsKey, context, facade)
                .map(CastUtil::castAsBoolean)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Integer> getConfigValueAsInteger(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getConfigValue(settingsKey, context, facade)
                .map(CastUtil::castAsInteger);
    }

    @Override
    public Mono<Long> getConfigValueAsLong(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getConfigValue(settingsKey, context, facade)
                .map(CastUtil::castAsLong);
    }

    @Override
    public Mono<Double> getConfigValueAsDouble(String settingsKey, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getConfigValue(settingsKey, context, facade)
                .map(CastUtil::castAsDouble);
    }

}
