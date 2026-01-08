package com.lyshra.open.app.core.exception.plugin;

import com.lyshra.open.app.core.engine.plugin.impl.LyshraOpenAppPluginDescriptor;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppPluginAlreadyRegistered extends RuntimeException {
    private final ILyshraOpenAppPluginIdentifier identifier;
    private final LyshraOpenAppPluginDescriptor existingDescriptor;
    private final LyshraOpenAppPluginDescriptor newDescriptor;

    public LyshraOpenAppPluginAlreadyRegistered(
            ILyshraOpenAppPluginIdentifier identifier,
            LyshraOpenAppPluginDescriptor existingDescriptor,
            LyshraOpenAppPluginDescriptor newDescriptor) {

        super("Plugin already registered. " +
                        "Identifier: ["+ identifier +"], " +
                        "Existing plugin directory: ["+ existingDescriptor.getPluginDir().toAbsolutePath() +"], " +
                        "New plugin directory: ["+ newDescriptor.getPluginDir().toAbsolutePath() +"]"
        );

        this.identifier = identifier;
        this.existingDescriptor = existingDescriptor;
        this.newDescriptor = newDescriptor;
    }
}
