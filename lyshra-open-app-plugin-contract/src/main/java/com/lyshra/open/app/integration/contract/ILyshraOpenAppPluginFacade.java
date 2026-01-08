package com.lyshra.open.app.integration.contract;

public interface ILyshraOpenAppPluginFacade {
    ILyshraOpenAppExpressionEvaluator getExpressionExecutor();
    ILyshraOpenAppObjectMapper getObjectMapper();
    ILyshraOpenAppSystemConfigEngine getConfigEngine();
}
