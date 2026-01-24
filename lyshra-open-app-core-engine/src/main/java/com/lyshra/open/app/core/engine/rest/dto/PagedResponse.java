package com.lyshra.open.app.core.engine.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paginated response wrapper.
 *
 * @param <T> the type of items in the response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

    /**
     * The list of items for the current page.
     */
    private List<T> items;

    /**
     * Total number of items across all pages.
     */
    private long totalCount;

    /**
     * Current page offset.
     */
    private int offset;

    /**
     * Page size limit.
     */
    private int limit;

    /**
     * Whether there are more items after this page.
     */
    private boolean hasMore;

    /**
     * Creates a paged response from items and total count.
     */
    public static <T> PagedResponse<T> of(List<T> items, long totalCount, int offset, int limit) {
        return PagedResponse.<T>builder()
                .items(items)
                .totalCount(totalCount)
                .offset(offset)
                .limit(limit)
                .hasMore(offset + items.size() < totalCount)
                .build();
    }
}
