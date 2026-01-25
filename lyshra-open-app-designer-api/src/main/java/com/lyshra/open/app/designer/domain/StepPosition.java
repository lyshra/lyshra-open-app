package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Position of a step in the visual designer canvas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepPosition {

    private double x;
    private double y;
    private double width;
    private double height;
}
