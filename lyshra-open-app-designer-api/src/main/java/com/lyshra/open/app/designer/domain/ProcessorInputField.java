package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Describes an input field for a processor in the designer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessorInputField {

    private String name;
    private String displayName;
    private String description;
    private ProcessorFieldType type;
    private boolean required;
    private Object defaultValue;
    private List<FieldOption> options;
    private Map<String, Object> validation;
    private String expressionSupport;

    /**
     * Gets the default value as Optional.
     *
     * @return Optional containing the default value
     */
    public Optional<Object> getDefaultValueOptional() {
        return Optional.ofNullable(defaultValue);
    }
}
