package com.lyshra.open.app.core.engine.plugin;

import java.nio.file.Path;

public interface ILyshraOpenAppPluginLoader {
    void loadAllPlugins(Path pluginsRootDirectory);
}
