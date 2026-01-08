package com.lyshra.open.app.core.engine.config.evaluator.impl;

import com.lyshra.open.app.core.engine.config.evaluator.ILyshraOpenAppSystemConfigEvaluator;
import com.lyshra.open.app.core.engine.config.models.LyshraOpenAppSystemConfig;
import com.lyshra.open.app.core.engine.config.models.VariantConfig;
import com.lyshra.open.app.integration.constant.LyshraOpenAppConstants;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;

import java.util.Map;

public class VariantEvaluator implements ILyshraOpenAppSystemConfigEvaluator {

    @Override
    public String evaluate(LyshraOpenAppSystemConfig config, ILyshraOpenAppContext context) {
        VariantConfig vc = config.getVariantConfig();
        Map<String, String> variants = vc.getVariants();
        return variants.getOrDefault(vc.getActive(), variants.get(LyshraOpenAppConstants.DEFAULT_CONFIG));
    }

}
