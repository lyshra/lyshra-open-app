package com.lyshra.open.app.integration.contract;

public interface ILyshraOpenAppExpressionEvaluator {
    Object evaluate(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    Boolean evaluateBoolean(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    Integer evaluateInteger(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    Long evaluateLong(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    Double evaluateDouble(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
    String evaluateString(ILyshraOpenAppExpression expression, ILyshraOpenAppContext context, ILyshraOpenAppPluginFacade facade);
}
