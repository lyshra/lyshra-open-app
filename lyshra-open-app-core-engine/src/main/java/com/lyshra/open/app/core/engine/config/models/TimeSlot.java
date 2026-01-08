package com.lyshra.open.app.core.engine.config.models;

import lombok.Data;

import java.time.LocalTime;

@Data
public class TimeSlot {
    private LocalTime start;
    private LocalTime end;
    private String value;
}
