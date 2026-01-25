package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A point on a connection path in the designer canvas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionPoint {

    private double x;
    private double y;
}
