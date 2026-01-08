package com.lyshra.open.app.core.engine.plugin;

import com.lyshra.open.app.integration.ILyshraOpenAppPluginProvider;

import java.nio.file.Path;

public interface ILyshraOpenAppPluginLoader {
    void loadAllPlugins(Path pluginsRootDirectory);

    // Only for testing purposes
    void loadPlugin(ILyshraOpenAppPluginProvider pluginProvider);
}
