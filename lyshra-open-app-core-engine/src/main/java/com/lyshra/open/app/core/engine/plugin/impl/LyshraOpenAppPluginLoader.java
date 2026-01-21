package com.lyshra.open.app.core.engine.plugin.impl;

import com.lyshra.open.app.core.engine.ILyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.LyshraOpenAppFacade;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginLoader;
import com.lyshra.open.app.core.exception.LyshraOpenAppRuntimeException;
import com.lyshra.open.app.core.exception.plugin.LyshraOpenAppPluginAlreadyRegistered;
import com.lyshra.open.app.integration.ILyshraOpenAppPluginProvider;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
public final class LyshraOpenAppPluginLoader implements ILyshraOpenAppPluginLoader {

    private final ILyshraOpenAppFacade facade;
    private final Set<String> scannedPluginDirectories = new LinkedHashSet<>();

    private LyshraOpenAppPluginLoader() {
        this.facade = LyshraOpenAppFacade.getInstance();
    }

    public static LyshraOpenAppPluginLoader getInstance() {
        return SingletonHelper.INSTANCE;
    }

    private static final class SingletonHelper {
        private static final LyshraOpenAppPluginLoader INSTANCE = new LyshraOpenAppPluginLoader();
    }

    @Override
    public synchronized void loadAllPlugins(Path pluginsRootDirectory) {

        if (scannedPluginDirectories.contains(pluginsRootDirectory.toAbsolutePath().toString())) {
            return;
        }

        if (!Files.isDirectory(pluginsRootDirectory)) {
            throw new IllegalStateException("Plugins directory Not Found: " + pluginsRootDirectory);
        }

        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(pluginsRootDirectory)) {
            for (Path pluginDir : dirs) {
                if (Files.isDirectory(pluginDir)) {
                    loadPlugin(pluginDir);
                }
            }
            scannedPluginDirectories.add(pluginsRootDirectory.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new LyshraOpenAppRuntimeException("Failed to scan plugins", e);
        }
    }

    @Override
    public void loadPlugin(ILyshraOpenAppPluginProvider pluginProvider) {
        try {
            createAndRegisterPlugin(
                    pluginProvider,
                    new LyshraOpenAppPluginClassLoader(new URL[] {}, getClass().getClassLoader()),
                    Paths.get(".")
            );
        } catch (LyshraOpenAppPluginAlreadyRegistered e) {
            // only logging it, since this method is exposed only for testing purposes
            log.warn("Plugin already registered: [{}]", e.getIdentifier());
        }
    }

    private void loadPlugin(Path pluginDir) {
        try {
            URL[] jarUrls = getJars(pluginDir);
            if (jarUrls.length == 0) return;

            ClassLoader parentClassLoader = getClass().getClassLoader();
            LyshraOpenAppPluginClassLoader lyshraOpenAppPluginClassLoader = new LyshraOpenAppPluginClassLoader(jarUrls, parentClassLoader);
            ServiceLoader<ILyshraOpenAppPluginProvider> lyshraOpenAppPluginProviders = ServiceLoader.load(ILyshraOpenAppPluginProvider.class, lyshraOpenAppPluginClassLoader);

            for (ILyshraOpenAppPluginProvider provider : lyshraOpenAppPluginProviders) {
                createAndRegisterPlugin(provider, lyshraOpenAppPluginClassLoader, pluginDir);
            }

        } catch (Exception e) {
            log.error("Failed to load plugin from directory: [{}]", pluginDir.toAbsolutePath(), e);
        }
    }

    private void createAndRegisterPlugin(
            ILyshraOpenAppPluginProvider provider,
            LyshraOpenAppPluginClassLoader classLoader,
            Path pluginDir) {

        ILyshraOpenAppPlugin lyshraOpenAppPlugin = provider.create(facade);
        LyshraOpenAppPluginDescriptor lyshraOpenAppPluginDescriptor = new LyshraOpenAppPluginDescriptor(
                pluginDir,
                classLoader,
                lyshraOpenAppPlugin
        );

        ILyshraOpenAppPluginIdentifier identifier = lyshraOpenAppPlugin.getIdentifier();
        LyshraOpenAppPluginFactory pluginFactory = (LyshraOpenAppPluginFactory) facade.getPluginFactory();
        pluginFactory.put(identifier, lyshraOpenAppPluginDescriptor);
        log.info("Loaded plugin: [{}]", identifier);
    }

    private URL[] getJars(Path pluginDir) throws IOException {
        try (Stream<Path> list = Files.walk(pluginDir)) {
            List<Path> jarPathList = list
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jar")).toList();
            URL[] jarUrls = new URL[jarPathList.size()];
            for (int i = 0; i < jarPathList.size(); i++) {
                jarUrls[i] = jarPathList.get(i).toUri().toURL();
            }
            return jarUrls;
        }
    }

}


