package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Metadata about a processor for the designer UI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessorMetadata {

    private String pluginOrganization;
    private String pluginModule;
    private String pluginVersion;
    private String processorName;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private List<ProcessorInputField> inputFields;
    private List<String> possibleBranches;
    private Map<String, Object> sampleConfig;

    /**
     * Gets the full processor identifier.
     *
     * @return the processor identifier
     */
    public String getIdentifier() {
        return String.format("%s/%s/%s:%s", pluginOrganization, pluginModule, pluginVersion, processorName);
    }
}
