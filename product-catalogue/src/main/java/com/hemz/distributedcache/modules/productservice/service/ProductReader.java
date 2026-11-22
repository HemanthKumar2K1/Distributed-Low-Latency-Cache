package com.hemz.distributedcache.modules.productservice.service;

import com.hemz.distributedcache.modules.productservice.dto.ProductResponse;
import com.hemz.distributedcache.modules.productservice.repo.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads one product from PostgreSQL. The cache calls it only on a miss.
 * Its own bean, so the transaction (and DB connection) starts only on a miss,
 * never before the cache is checked.
 */
@Component
public class ProductReader {

    private final ProductRepository repository;

    public ProductReader(ProductRepository repository) {
        this.repository = repository;
    }

    /** The product, or null if there is no such id. */
    @Transactional(readOnly = true)
    public ProductResponse loadById(long id) {
        return repository.findById(id).map(ProductResponse::from).orElse(null);
    }
}

