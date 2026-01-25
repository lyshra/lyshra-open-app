package com.lyshra.open.app.designer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * DTO containing execution statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStatistics {

    private long totalExecutions;
    private long runningExecutions;
    private long completedExecutions;
    private long failedExecutions;
    private long cancelledExecutions;
    private double successRate;
    private Duration averageDuration;
    private Duration minDuration;
    private Duration maxDuration;
    private long executionsToday;
    private long executionsThisWeek;
    private long executionsThisMonth;
}
