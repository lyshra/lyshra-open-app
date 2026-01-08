package com.lyshra.open.app.core.engine.config.models;

import com.lyshra.open.app.integration.models.commons.LyshraOpenAppExpression;
import lombok.Data;

import java.util.Map;

@Data
public class ContextParameterConfig {
    private LyshraOpenAppExpression expression;
    private Map<String, String> values;
}
