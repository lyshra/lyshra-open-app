package com.lyshra.open.app.core.exception.plugin;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppPluginI18NNotFound extends RuntimeException {
    private final ILyshraOpenAppPluginIdentifier identifier;

    public LyshraOpenAppPluginI18NNotFound(ILyshraOpenAppPluginIdentifier identifier) {
        super("Plugin I18N Config Not Found. Identifier: ["+ identifier +"]");
        this.identifier = identifier;
    }
}
