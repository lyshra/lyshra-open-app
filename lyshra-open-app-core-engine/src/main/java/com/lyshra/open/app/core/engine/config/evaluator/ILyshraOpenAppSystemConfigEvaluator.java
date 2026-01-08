package com.lyshra.open.app.core.engine.config.evaluator;

import com.lyshra.open.app.core.engine.config.models.LyshraOpenAppSystemConfig;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;

public interface ILyshraOpenAppSystemConfigEvaluator {
    String evaluate(LyshraOpenAppSystemConfig config, ILyshraOpenAppContext context);
}
