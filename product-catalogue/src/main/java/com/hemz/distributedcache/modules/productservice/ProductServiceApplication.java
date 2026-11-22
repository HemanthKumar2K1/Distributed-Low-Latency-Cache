package com.hemz.distributedcache.modules.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Base package is "productservice", but this single Spring context also hosts the
 * "loadtestservice" module (a sibling package). scanBasePackages/ConfigurationPropertiesScan
 * are widened to "modules" so those beans and @ConfigurationProperties are picked up too.
 */
@SpringBootApplication(scanBasePackages = "com.hemz.distributedcache.modules")
@ConfigurationPropertiesScan(basePackages = "com.hemz.distributedcache.modules")
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }

}
