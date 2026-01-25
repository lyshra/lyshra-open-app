package com.lyshra.open.app.designer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata for the visual designer canvas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignerMetadata {

    private double canvasWidth;
    private double canvasHeight;
    private double zoomLevel;
    private double panX;
    private double panY;
    private String gridType;
    private boolean snapToGrid;
    private int gridSize;

    /**
     * Creates default designer metadata.
     *
     * @return default designer metadata
     */
    public static DesignerMetadata defaults() {
        return DesignerMetadata.builder()
                .canvasWidth(2000)
                .canvasHeight(1500)
                .zoomLevel(1.0)
                .panX(0)
                .panY(0)
                .gridType("dots")
                .snapToGrid(true)
                .gridSize(20)
                .build();
    }
}
