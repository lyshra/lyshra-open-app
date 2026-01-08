package com.lyshra.open.app.core.engine.plugin;

import com.lyshra.open.app.core.engine.message.ILyshraOpenAppPluginMessageSource;
import com.lyshra.open.app.core.engine.plugin.impl.LyshraOpenAppPluginClassLoader;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import jakarta.validation.ValidatorFactory;

import java.nio.file.Path;

public interface ILyshraOpenAppPluginDescriptor {
    Path getPluginDir();
    LyshraOpenAppPluginClassLoader getClassLoader();
    ILyshraOpenAppPlugin getPlugin();
    ILyshraOpenAppPluginMessageSource getMessageSource();
    ValidatorFactory getValidatorFactory();
}
