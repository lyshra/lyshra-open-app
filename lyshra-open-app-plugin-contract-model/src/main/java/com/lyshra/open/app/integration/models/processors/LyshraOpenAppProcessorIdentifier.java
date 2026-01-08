package com.lyshra.open.app.integration.models.processors;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppPluginIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import lombok.Data;

@Data
public class LyshraOpenAppProcessorIdentifier implements ILyshraOpenAppProcessorIdentifier {
    private final String organization;
    private final String module;
    private final String version;
    private final String processorName;

    public LyshraOpenAppProcessorIdentifier(ILyshraOpenAppPluginIdentifier lyshraOpenAppPluginIdentifier, String processorName) {
        this.organization = lyshraOpenAppPluginIdentifier.getOrganization();
        this.module = lyshraOpenAppPluginIdentifier.getModule();
        this.version = lyshraOpenAppPluginIdentifier.getVersion();
        this.processorName = processorName;
    }
}
