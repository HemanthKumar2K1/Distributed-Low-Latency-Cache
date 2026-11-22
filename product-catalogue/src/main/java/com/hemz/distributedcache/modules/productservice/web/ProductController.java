package com.hemz.distributedcache.modules.productservice.web;

import com.hemz.distributedcache.modules.productservice.dto.PageResponse;
import com.hemz.distributedcache.modules.productservice.dto.ProductResponse;
import com.hemz.distributedcache.modules.productservice.service.ProductService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/")
@Validated
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("lists")
    public PageResponse<ProductResponse> getProductList(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(name = "per_page", defaultValue = "20") @Min(1) @Max(100) int perPage) {
        return productService.getProductsList(page, perPage);
    }

    @GetMapping("{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        log.debug("Fetching product with id: {}", id);
        return productService.getProductById(id);
    }

}
