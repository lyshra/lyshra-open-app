package com.lyshra.open.app.core.engine.expression;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpression;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppExpressionEvaluator;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;

public class LyshraOpenAppExpressionEvaluator implements ILyshraOpenAppExpressionEvaluator {

    private LyshraOpenAppExpressionEvaluator() {}

    public static ILyshraOpenAppExpressionEvaluator getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static final class SingletonHolder {
        private static final ILyshraOpenAppExpressionEvaluator INSTANCE = new LyshraOpenAppExpressionEvaluator();
    }

    private ILyshraOpenAppExpressionEvaluator getEvaluator(ILyshraOpenAppExpression expression) {
        return ExpressionEvaluatorFactory.getInstance().getEvaluator(expression.getExpressionType());
    }

    @Override
    public Object evaluate(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getEvaluator(expression).evaluate(expression, context, facade);
    }

    @Override
    public Boolean evaluateBoolean(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getEvaluator(expression).evaluateBoolean(expression, context, facade);
    }

    @Override
    public Integer evaluateInteger(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getEvaluator(expression).evaluateInteger(expression, context, facade);
    }

    @Override
    public Double evaluateDouble(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getEvaluator(expression).evaluateDouble(expression, context, facade);
    }

    @Override
    public Long evaluateLong(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getEvaluator(expression).evaluateLong(expression, context, facade);
    }

    @Override
    public String evaluateString(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade) {
        return getEvaluator(expression).evaluateString(expression, context, facade);
    }

}
