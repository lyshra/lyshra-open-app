package com.lyshra.open.app.core.engine.config.models;

import lombok.Data;

import java.time.Instant;

@Data
public class DateWindow {
    private Instant start;
    private Instant end;
    private String value;
}
