package com.lyshra.open.app.core.engine.config.evaluator.impl;

import com.lyshra.open.app.core.engine.config.evaluator.ILyshraOpenAppSystemConfigEvaluator;
import com.lyshra.open.app.core.engine.config.models.LyshraOpenAppSystemConfig;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;


public class FixedEvaluator implements ILyshraOpenAppSystemConfigEvaluator {

    @Override
    public String evaluate(LyshraOpenAppSystemConfig config, ILyshraOpenAppContext context) {
        return config.getFixedConfig();
    }

}
