package com.shopflow.catalog.dto;

import com.shopflow.catalog.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(requiredProperties = {"id", "name", "description", "price", "stock", "category"})
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
