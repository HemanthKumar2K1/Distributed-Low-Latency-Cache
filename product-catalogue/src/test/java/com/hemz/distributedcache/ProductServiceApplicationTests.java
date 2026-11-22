package com.hemz.distributedcache;

import com.hemz.distributedcache.modules.productservice.ProductServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Starts the whole app, so this one needs PostgreSQL running. */
@SpringBootTest(classes = ProductServiceApplication.class)
class ProductServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
