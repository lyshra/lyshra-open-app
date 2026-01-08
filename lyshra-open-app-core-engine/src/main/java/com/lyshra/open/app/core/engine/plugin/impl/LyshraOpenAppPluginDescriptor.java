package com.lyshra.open.app.core.engine.plugin.impl;

import com.lyshra.open.app.core.engine.message.LyshraOpenAppPluginMessageSource;
import com.lyshra.open.app.core.engine.plugin.ILyshraOpenAppPluginDescriptor;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.hibernate.validator.resourceloading.PlatformResourceBundleLocator;

import java.nio.file.Path;

@Getter
@RequiredArgsConstructor
public final class LyshraOpenAppPluginDescriptor implements ILyshraOpenAppPluginDescriptor {
    private final Path pluginDir;
    private final LyshraOpenAppPluginClassLoader classLoader;
    private final ILyshraOpenAppPlugin plugin;
    private volatile LyshraOpenAppPluginMessageSource messageSource;
    private volatile ValidatorFactory validatorFactory;

    public LyshraOpenAppPluginMessageSource getMessageSource() {
        if (messageSource == null) {
            synchronized (this) {
                if (messageSource == null) {
                    messageSource = new LyshraOpenAppPluginMessageSource(plugin.getI18n(), classLoader);
                }
            }
        }
        return messageSource;
    }

    public ValidatorFactory getValidatorFactory() {
        if (validatorFactory == null) {
            synchronized (this) {
                if (validatorFactory == null) {
                    String resourceBundleBasenamePath = plugin.getI18n().getResourceBundleBasenamePath();
                    PlatformResourceBundleLocator bundleLocator = new PlatformResourceBundleLocator(
                            resourceBundleBasenamePath,
                            classLoader
                    );
                    validatorFactory = Validation.byDefaultProvider()
                            .configure()
                            .messageInterpolator(new ResourceBundleMessageInterpolator(bundleLocator))
                            .buildValidatorFactory();
                }
            }
        }
        return validatorFactory;
    }

}
