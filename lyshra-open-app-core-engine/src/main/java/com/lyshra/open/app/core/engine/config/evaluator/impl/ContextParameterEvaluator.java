package com.lyshra.open.app.core.engine.config.evaluator.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.config.evaluator.ILyshraOpenAppSystemConfigEvaluator;
import com.lyshra.open.app.core.engine.config.models.ContextParameterConfig;
import com.lyshra.open.app.core.engine.config.models.LyshraOpenAppSystemConfig;
import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;

import java.util.Map;

public class ContextParameterEvaluator implements ILyshraOpenAppSystemConfigEvaluator {

    private final ILyshraOpenAppFacade facade;

    public ContextParameterEvaluator() {
        this.facade = LyshraOpenAppFacade.getInstance();
    }

    @Override
    public String evaluate(LyshraOpenAppSystemConfig config, ILyshraOpenAppContext context) {
        ContextParameterConfig cpc = config.getContextParameterConfig();
        ILyshraOpenAppExpressionEvaluator executor = facade.getExpressionExecutor();
        String resolvedKey = executor.evaluateString(cpc.getExpression(), context, facade);
        Map<String, String> values = cpc.getValues();
        return values.getOrDefault(resolvedKey, values.get(LyshraOpenAppConstants.DEFAULT_CONFIG));
    }

}
