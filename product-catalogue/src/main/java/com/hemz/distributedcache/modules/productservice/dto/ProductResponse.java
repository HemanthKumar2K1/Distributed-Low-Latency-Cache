package com.hemz.distributedcache.modules.productservice.dto;

import com.hemz.distributedcache.modules.productservice.model.Product;

import java.math.BigDecimal;

/**
 * What the outside world sees. Immutable, so it is safe to cache.
 * Lives in dto (not web) because the service returns it: the service
 * must never depend on the web layer.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Long categoryId,
        boolean active
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getPrice(), p.getCategoryId(), p.isActive());
    }
}
