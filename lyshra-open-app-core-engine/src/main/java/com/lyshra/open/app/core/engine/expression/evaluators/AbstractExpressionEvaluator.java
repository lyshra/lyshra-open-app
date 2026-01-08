package com.lyshra.open.app.core.engine.expression.evaluators;

import com.lyshra.open.app.core.util.CastUtil;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppExpressionType;

import java.util.Optional;

public abstract class AbstractExpressionEvaluator implements ILyshraOpenAppExpressionEvaluator {

    public abstract LyshraOpenAppExpressionType getEvaluatorType();

    @Override
    public Boolean evaluateBoolean(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return Optional.ofNullable(evaluate(expression, context, facade))
                .map(CastUtil::castAsBoolean)
                .orElse(false);
    }

    @Override
    public Integer evaluateInteger(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return Optional.ofNullable(evaluate(expression, context, facade))
                .map(CastUtil::castAsInteger)
                .orElse(null);
    }

    @Override
    public Double evaluateDouble(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return Optional.ofNullable(evaluate(expression, context, facade))
                .map(CastUtil::castAsDouble)
                .orElse(null);
    }

    @Override
    public Long evaluateLong(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return Optional.ofNullable(evaluate(expression, context, facade))
                .map(CastUtil::castAsLong)
                .orElse(null);
    }

    @Override
    public String evaluateString(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return Optional.ofNullable(evaluate(expression, context, facade))
                .map(CastUtil::castAsString)
                .orElse(null);
    }

}
