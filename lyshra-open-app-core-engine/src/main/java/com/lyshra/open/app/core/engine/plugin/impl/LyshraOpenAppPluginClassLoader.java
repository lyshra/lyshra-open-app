package com.lyshra.open.app.core.engine.plugin.impl;

import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.net.URLClassLoader;

@Slf4j
public final class LyshraOpenAppPluginClassLoader extends URLClassLoader {

    public LyshraOpenAppPluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {

        // Never override Java core classes
        if (name.startsWith("java.")) {
            return super.loadClass(name, resolve);
        }

        // Try plugin first
        try {
            Class<?> clazz = findClass(name);
            if (resolve) {
                resolveClass(clazz);
            }
            return clazz;
        } catch (ClassNotFoundException e) {
//            log.error("Plugin ClassNotFoundException: [{}]", name, e);
        }

        return super.loadClass(name, resolve);
    }
}