package com.lyshra.open.app.core.engine.config.models;

import lombok.Data;

import java.time.Instant;

@Data
public class LyshraOpenAppSystemConfig {
    private Instant createdAt;
    private Instant lastModifiedAt;
    private String key;
    private String description;
    private LyshraOpenAppSystemConfigMode mode;
    private String fixedConfig;
    private VariantConfig variantConfig;
    private ContextParameterConfig contextParameterConfig;
    private PercentageConfig percentageConfig;
    private TimeSlotConfig timeSlotConfig;
    private DateTimeConfig dateTimeConfig;
}
