package com.hemz.distributedcache.modules.productservice.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A stable JSON shape for paged results.
 * Serialising Spring's PageImpl directly is discouraged: its JSON shape is not a stable contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                List.copyOf(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
