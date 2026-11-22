package com.hemz.distributedcache.modules.productservice.repo;

import com.hemz.distributedcache.modules.productservice.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

}
