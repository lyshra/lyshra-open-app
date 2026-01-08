package com.lyshra.open.app.core.engine.config.models;

import lombok.Data;

import java.util.Map;

@Data
public class VariantConfig {
    private String active;
    private Map<String, String> variants;
}
