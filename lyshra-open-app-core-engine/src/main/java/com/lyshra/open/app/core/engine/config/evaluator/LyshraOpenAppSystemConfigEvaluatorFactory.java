package com.lyshra.open.app.core.engine.config.evaluator;


import com.lyshra.open.app.core.engine.config.evaluator.impl.ContextParameterEvaluator;
import com.lyshra.open.app.core.engine.config.evaluator.impl.DailyTimeSlotEvaluator;
import com.lyshra.open.app.core.engine.config.evaluator.impl.DateTimeSlotEvaluator;
import com.lyshra.open.app.core.engine.config.evaluator.impl.FixedEvaluator;
import com.lyshra.open.app.core.engine.config.evaluator.impl.PercentageEvaluator;
import com.lyshra.open.app.core.engine.config.evaluator.impl.VariantEvaluator;
import com.lyshra.open.app.core.engine.config.models.LyshraOpenAppSystemConfigMode;

import java.util.EnumMap;
import java.util.Map;

public class LyshraOpenAppSystemConfigEvaluatorFactory {

    private final Map<LyshraOpenAppSystemConfigMode, ILyshraOpenAppSystemConfigEvaluator> REGISTRY;

    private LyshraOpenAppSystemConfigEvaluatorFactory() {
        REGISTRY = new EnumMap<>(LyshraOpenAppSystemConfigMode.class);
        REGISTRY.put(LyshraOpenAppSystemConfigMode.FIXED, new FixedEvaluator());
        REGISTRY.put(LyshraOpenAppSystemConfigMode.VARIANT, new VariantEvaluator());
        REGISTRY.put(LyshraOpenAppSystemConfigMode.CONTEXT_PARAMETER, new ContextParameterEvaluator());
        REGISTRY.put(LyshraOpenAppSystemConfigMode.PERCENTAGE, new PercentageEvaluator());
        REGISTRY.put(LyshraOpenAppSystemConfigMode.DAILY_TIME_SLOT, new DailyTimeSlotEvaluator());
        REGISTRY.put(LyshraOpenAppSystemConfigMode.DATE_TIME_SLOT, new DateTimeSlotEvaluator());
    }

    private static final class SingletonHelper {
        private static final LyshraOpenAppSystemConfigEvaluatorFactory INSTANCE = new LyshraOpenAppSystemConfigEvaluatorFactory();
    }

    public static LyshraOpenAppSystemConfigEvaluatorFactory getInstance() {
        return LyshraOpenAppSystemConfigEvaluatorFactory.SingletonHelper.INSTANCE;
    }

    public ILyshraOpenAppSystemConfigEvaluator get(LyshraOpenAppSystemConfigMode configMode) {
        ILyshraOpenAppSystemConfigEvaluator evaluator = REGISTRY.get(configMode);
        if (evaluator == null) {
            throw new IllegalStateException("Unsupported mode: " + configMode);
        }
        return evaluator;
    }

}
