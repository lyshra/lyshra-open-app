package com.lyshra.open.app.core.engine.config.models;

import lombok.Data;

import java.util.List;

@Data
public class TimeSlotConfig {
    private String defaultValue;
    private List<TimeSlot> slots;
}
