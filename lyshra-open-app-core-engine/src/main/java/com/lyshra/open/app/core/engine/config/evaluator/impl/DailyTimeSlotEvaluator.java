package com.lyshra.open.app.core.engine.config.evaluator.impl;

import com.lyshra.open.app.core.engine.config.evaluator.ILyshraOpenAppSystemConfigEvaluator;
import com.lyshra.open.app.core.engine.config.models.LyshraOpenAppSystemConfig;
import com.lyshra.open.app.core.engine.config.models.TimeSlot;
import com.lyshra.open.app.core.engine.config.models.TimeSlotConfig;
import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;

import java.time.LocalTime;

public class DailyTimeSlotEvaluator implements ILyshraOpenAppSystemConfigEvaluator {

    @Override
    public String evaluate(LyshraOpenAppSystemConfig config, ILyshraOpenAppContext context) {
        LocalTime now = LocalTime.now();
        TimeSlotConfig timeSlotConfig = config.getTimeSlotConfig();
        for (TimeSlot slot : timeSlotConfig.getSlots()) {
            if (matches(slot, now)) {
                return slot.getValue();
            }
        }
        return config.getTimeSlotConfig().getDefaultValue();
    }

    private boolean matches(TimeSlot slot, LocalTime now) {
        LocalTime start = slot.getStart();
        LocalTime end = slot.getEnd();
        return (now.equals(start) || now.isAfter(start)) && (now.equals(end) || now.isBefore(end));
    }

}
