package com.lyshra.open.app.core.exception.plugin;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppPluginNotFound extends RuntimeException {
    private final ILyshraOpenAppPluginIdentifier identifier;

    public LyshraOpenAppPluginNotFound(ILyshraOpenAppPluginIdentifier identifier) {
        super("Plugin Not Found: " + identifier);
        this.identifier = identifier;
    }
}
