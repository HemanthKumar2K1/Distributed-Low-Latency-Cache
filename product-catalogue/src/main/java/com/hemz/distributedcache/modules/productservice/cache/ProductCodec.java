package com.hemz.distributedcache.modules.productservice.cache;

import com.hemz.distributedcache.modules.cacheclient.aside.ValueCodec;
import com.hemz.distributedcache.modules.productservice.dto.ProductResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** ProductResponse <-> JSON bytes, with Spring's Jackson 3 mapper. */
@Component
public class ProductCodec implements ValueCodec<ProductResponse> {

    private final ObjectMapper mapper;

    public ProductCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public byte[] encode(ProductResponse value) {
        return mapper.writeValueAsBytes(value);
    }

    @Override
    public ProductResponse decode(byte[] bytes) {
        return mapper.readValue(bytes, ProductResponse.class);
    }
}

