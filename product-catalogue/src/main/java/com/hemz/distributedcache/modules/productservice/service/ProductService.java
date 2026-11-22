package com.hemz.distributedcache.modules.productservice.service;

import com.hemz.distributedcache.modules.cacheclient.aside.CacheAsideTemplate;
import com.hemz.distributedcache.modules.productservice.cache.CacheKeys;
import com.hemz.distributedcache.modules.productservice.cache.CacheSwitch;
import com.hemz.distributedcache.modules.productservice.cache.ProductCodec;
import com.hemz.distributedcache.modules.productservice.dto.PageResponse;
import com.hemz.distributedcache.modules.productservice.dto.ProductResponse;
import com.hemz.distributedcache.modules.productservice.repo.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductReader productReader;
    private final CacheAsideTemplate cacheAside;
    private final ProductCodec codec;
    private final CacheSwitch cacheSwitch;

    public ProductService(ProductRepository productRepository, ProductReader productReader,
                          CacheAsideTemplate cacheAside, ProductCodec codec, CacheSwitch cacheSwitch) {
        this.productRepository = productRepository;
        this.productReader = productReader;
        this.cacheAside = cacheAside;
        this.codec = codec;
        this.cacheSwitch = cacheSwitch;
    }

    /** The list is read straight from PostgreSQL (only single products are cached). */
    public PageResponse<ProductResponse> getProductsList(int page, int perPage) {
        Pageable pageable = PageRequest.of(page, perPage, Sort.by("id"));
        return PageResponse.from(productRepository.findAll(pageable).map(ProductResponse::from));
    }

    /**
     * Cache on:  cache first; on a miss, read PostgreSQL and keep a copy.
     * Cache off: straight to PostgreSQL. Nothing is counted.
     */
    public ProductResponse getProductById(Long id) {
        ProductResponse product = cacheSwitch.isEnabled()
                ? cacheAside.get(CacheKeys.product(id), codec, () -> productReader.loadById(id))
                : productReader.loadById(id);
        if (product == null) {
            throw new ProductNotFoundException(id);
        }
        return product;
    }

}
