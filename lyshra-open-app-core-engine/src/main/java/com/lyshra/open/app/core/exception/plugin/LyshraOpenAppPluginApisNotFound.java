package com.lyshra.open.app.core.exception.plugin;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppPluginApisNotFound extends RuntimeException {
    private final ILyshraOpenAppPluginIdentifier identifier;

    public LyshraOpenAppPluginApisNotFound(ILyshraOpenAppPluginIdentifier identifier) {
        super("Plugin API Not Found. Identifier: ["+ identifier +"]");
        this.identifier = identifier;
    }
}
