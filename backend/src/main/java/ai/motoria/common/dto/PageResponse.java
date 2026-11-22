package ai.motoria.common.dto;

import java.util.List;

/**
 * Paged list returned by search endpoints: {@code {items, page, size, total}}.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total) {
}
