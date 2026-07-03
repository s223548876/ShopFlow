package com.shopflow.catalog.dto;

import com.shopflow.catalog.Product;

import java.math.BigDecimal;

public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        CategoryResponse category
) {

    public static ProductDetailResponse from(Product product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                CategoryResponse.from(product.getCategory())
        );
    }
}
