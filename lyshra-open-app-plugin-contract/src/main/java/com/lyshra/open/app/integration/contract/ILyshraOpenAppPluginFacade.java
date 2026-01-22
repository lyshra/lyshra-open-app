package com.lyshra.open.app.integration.contract;

import com.lyshra.open.app.integration.contract.api.ILyshraOpenAppApis;

public interface ILyshraOpenAppPluginFacade {
    ILyshraOpenAppExpressionEvaluator getExpressionExecutor();
    ILyshraOpenAppObjectMapper getObjectMapper();
    ILyshraOpenAppSystemConfigEngine getConfigEngine();

    /**
     * Gets the API definitions for a given plugin.
     *
     * @param pluginIdentifier the plugin identifier (e.g., "groupId/artifactId/version")
     * @return the API definitions for the plugin, or null if not found
     */
    ILyshraOpenAppApis getPluginApis(String pluginIdentifier);
}
