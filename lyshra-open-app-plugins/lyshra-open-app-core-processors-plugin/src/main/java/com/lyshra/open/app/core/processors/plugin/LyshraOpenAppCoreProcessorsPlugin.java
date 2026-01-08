package com.lyshra.open.app.core.processors.plugin;

import com.lyshra.open.app.core.processors.plugin.processors.IfProcessor;
import com.lyshra.open.app.core.processors.plugin.processors.SwitchProcessor;
import com.lyshra.open.app.integration.ILyshraOpenAppPluginProvider;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginFacade;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPlugin;
import com.lyshra.open.app.integration.models.LyshraOpenAppPlugin;

public class LyshraOpenAppCoreProcessorsPlugin implements ILyshraOpenAppPluginProvider {

    @Override
    public ILyshraOpenAppPlugin create(ILyshraOpenAppPluginFacade lyshraOpenAppFacade) {
        return LyshraOpenAppPlugin
                .builder()
                .identifier(builder -> builder
                        .organization("com.lyshra.open.app")
                        .module("core")
                        .version("1.0.0")
                )
                .documentationResourcePath("documentation.md")
                .processors(builder -> builder
                        .processor(IfProcessor::build)
                        .processor(SwitchProcessor::build)
                )
                .i18n(builder -> builder
                        .resourceBundleBasenamePath("i18n/messages")
                )
                .build();
    }
}
