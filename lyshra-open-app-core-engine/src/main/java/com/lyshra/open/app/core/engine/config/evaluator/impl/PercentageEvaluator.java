package com.lyshra.open.app.core.engine.config.evaluator.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.config.evaluator.ILyshraOpenAppSystemConfigEvaluator;
import com.lyshra.open.app.core.engine.config.models.Distribution;
import com.lyshra.open.app.core.engine.config.models.LyshraOpenAppSystemConfig;
import com.lyshra.open.app.core.engine.config.models.PercentageConfig;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;
import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PercentageEvaluator implements ILyshraOpenAppSystemConfigEvaluator {

    public static final int PERCENTAGE_BASE = 100;

    private final ILyshraOpenAppFacade facade;

    public PercentageEvaluator() {
        this.facade = LyshraOpenAppFacade.getInstance();
    }

    @Override
    public String evaluate(LyshraOpenAppSystemConfig config, ILyshraOpenAppContext context) {
        PercentageConfig pc = config.getPercentageConfig();
        int bucket = computeBucket(pc.getStickinessKey(), context);
        int cumulative = 0;
        List<Distribution> distributionList = pc.getDistribution();
        distributionList.sort(Comparator.comparingInt(Distribution::getPercentage));

        for (Distribution distribution : distributionList) {
            cumulative += distribution.getPercentage();
            if (bucket < cumulative) {
                return distribution.getValue();
            }
        }
        throw new IllegalStateException("Invalid percentage config");
    }

    private int computeBucket(List<String> keys, ILyshraOpenAppContext context) {
        ILyshraOpenAppExpressionEvaluator executor = facade.getExpressionExecutor();
        String resolvedJoinedKey = keys.stream()
                .map(key -> new LyshraOpenAppExpression(LyshraOpenAppExpressionType.SPEL, key))
                .map(expression -> executor.evaluateString(expression, context, facade))
                .collect(Collectors.joining(":"));
        return Math.abs(resolvedJoinedKey.hashCode() % PERCENTAGE_BASE);
    }

}
