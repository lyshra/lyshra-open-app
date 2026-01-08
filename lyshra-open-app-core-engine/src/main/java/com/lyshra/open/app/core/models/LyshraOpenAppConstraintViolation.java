package com.lyshra.open.app.core.models;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class LyshraOpenAppConstraintViolation implements Serializable {
    private final Class<?> clazz;
    private final String propertyPath;
    private final String messageTemplate;
    private final Map<String, String> templateVariables;
}
