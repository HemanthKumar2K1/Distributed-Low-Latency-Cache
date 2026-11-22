package com.hemz.distributedcache.modules.productservice.service;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("No product with id " + id);
    }
}

