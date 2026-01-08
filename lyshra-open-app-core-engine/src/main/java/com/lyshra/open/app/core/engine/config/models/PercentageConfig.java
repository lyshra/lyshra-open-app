package com.lyshra.open.app.core.engine.config.models;

import lombok.Data;

import java.util.List;

@Data
public class PercentageConfig {
    private List<String> stickinessKey;
    private List<Distribution> distribution;
}
