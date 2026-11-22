package com.hemz.distributedcache.modules.productservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One row of the products table. Read-only here: rows come from sql/schema-and-seed.sql. */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // BIGSERIAL: the database assigns the id
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;                             // never double for money

    // A plain column on purpose, not @ManyToOne.
    // A relationship would add a join or a lazy load to every read and
    // pollute the latency numbers this service exists to measure.
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** For JPA only. */
    protected Product() {
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public boolean isActive() {
        return active;
    }
}
