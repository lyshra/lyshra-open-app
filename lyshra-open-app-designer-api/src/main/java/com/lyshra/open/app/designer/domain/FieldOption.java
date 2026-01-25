package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An option for a select or multiselect field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldOption {

    private String value;
    private String label;
    private String description;
}
