package com.lyshra.open.app.core.exception.misc;

import lombok.Data;

@Data
public class LyshraOpenAppSystemConfigNotFound extends RuntimeException {
    private final String settingsKey;

    public LyshraOpenAppSystemConfigNotFound(String settingsKey) {
        super("System Config Not Found. Key: ["+ settingsKey +"]");
        this.settingsKey = settingsKey;
    }
}
