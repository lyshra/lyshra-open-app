package com.lyshra.open.app.core.exception.plugin;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppPluginDocumentationLoadFailed extends RuntimeException {
    private final ILyshraOpenAppPluginIdentifier identifier;
    private final Throwable cause;

    public LyshraOpenAppPluginDocumentationLoadFailed(ILyshraOpenAppPluginIdentifier identifier, Throwable cause) {
        super("Failed to load plugin documentation: " + identifier, cause);
        this.identifier = identifier;
        this.cause = cause;
    }
}
