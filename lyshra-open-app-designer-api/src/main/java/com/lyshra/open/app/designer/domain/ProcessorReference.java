package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reference to a processor in a plugin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessorReference {

    private String organization;
    private String module;
    private String version;
    private String processorName;

    /**
     * Creates a processor identifier string in the format "organization/module/version:processorName".
     *
     * @return the full processor identifier
     */
    public String toIdentifier() {
        return String.format("%s/%s/%s:%s", organization, module, version, processorName);
    }

    /**
     * Creates a ProcessorReference from an identifier string.
     *
     * @param identifier the identifier string in format "organization/module/version:processorName"
     * @return the ProcessorReference
     * @throws IllegalArgumentException if the identifier format is invalid
     */
    public static ProcessorReference fromIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Processor identifier cannot be null or blank");
        }

        String[] parts = identifier.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid processor identifier format: " + identifier);
        }

        String[] pluginParts = parts[0].split("/");
        if (pluginParts.length != 3) {
            throw new IllegalArgumentException("Invalid plugin identifier format: " + parts[0]);
        }

        return ProcessorReference.builder()
                .organization(pluginParts[0])
                .module(pluginParts[1])
                .version(pluginParts[2])
                .processorName(parts[1])
                .build();
    }
}
