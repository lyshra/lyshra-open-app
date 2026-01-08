package com.lyshra.open.app.core.engine.config.evaluator.impl;

import com.lyshra.open.app.core.engine.config.evaluator.ILyshraOpenAppSystemConfigEvaluator;
import com.lyshra.open.app.core.engine.config.models.DateTimeConfig;
import com.lyshra.open.app.core.engine.config.models.DateWindow;
import com.lyshra.open.app.core.engine.config.models.LyshraOpenAppSystemConfig;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;

import java.time.Instant;
import java.util.List;

public class DateTimeSlotEvaluator implements ILyshraOpenAppSystemConfigEvaluator {

    @Override
    public String evaluate(LyshraOpenAppSystemConfig config, ILyshraOpenAppContext context) {
        Instant now = Instant.now();
        DateTimeConfig dateTimeConfig = config.getDateTimeConfig();
        List<DateWindow> windows = dateTimeConfig.getWindows();
        for (DateWindow window: windows) {
            if (matches(window, now)) {
                return window.getValue();
            }
        }
        return config.getTimeSlotConfig().getDefaultValue();
    }

    private boolean matches(DateWindow window, Instant now) {
        Instant start = window.getStart();
        Instant end = window.getEnd();
        return (now.equals(start) || now.isAfter(start)) && (now.equals(end) || now.isBefore(end));
    }

}
