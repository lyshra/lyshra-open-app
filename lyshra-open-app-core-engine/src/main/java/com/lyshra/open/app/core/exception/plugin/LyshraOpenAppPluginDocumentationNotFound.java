package com.lyshra.open.app.core.exception.plugin;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppPluginDocumentationNotFound extends RuntimeException {
    private final ILyshraOpenAppPluginIdentifier identifier;

    public LyshraOpenAppPluginDocumentationNotFound(ILyshraOpenAppPluginIdentifier identifier) {
        super("Plugin Documentation Not Found: " + identifier);
        this.identifier = identifier;
    }
}
