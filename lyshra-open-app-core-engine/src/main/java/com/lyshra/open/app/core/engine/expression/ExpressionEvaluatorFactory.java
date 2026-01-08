package com.lyshra.open.app.core.engine.expression;

import com.lyshra.open.app.core.engine.expression.evaluators.spel.SpElJsonEvaluator;
import com.lyshra.open.app.core.engine.expression.evaluators.graalvm.GraalVmJsEvaluator;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;

import java.util.EnumMap;

public class ExpressionEvaluatorFactory {

    private final EnumMap<LyshraOpenAppExpressionType, ILyshraOpenAppExpressionEvaluator> evaluators = new EnumMap<>(LyshraOpenAppExpressionType.class);

    private ExpressionEvaluatorFactory() {
        SpElJsonEvaluator spElJsonEvaluator = new SpElJsonEvaluator();
        evaluators.put(spElJsonEvaluator.getEvaluatorType(), spElJsonEvaluator);

        GraalVmJsEvaluator graalVmJsEvaluator = new GraalVmJsEvaluator();
        evaluators.put(graalVmJsEvaluator.getEvaluatorType(), graalVmJsEvaluator);
    }

    public static ExpressionEvaluatorFactory getInstance() {
        return SingletonHelper.INSTANCE;
    }

    private static final class SingletonHelper {
        private static final ExpressionEvaluatorFactory INSTANCE = new ExpressionEvaluatorFactory();
    }

    public ILyshraOpenAppExpressionEvaluator getEvaluator(LyshraOpenAppExpressionType expressionType) {
        return evaluators.get(expressionType);
    }
}
