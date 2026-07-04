package com.shopflow.catalog.dto;

import com.shopflow.catalog.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(requiredProperties = {"id", "name", "price", "stock", "category"})
public record ProductSummaryResponse(
        Long id,
        String name,
        BigDecimal price,
        int stock,
        CategoryResponse category
) {

    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                CategoryResponse.from(product.getCategory())
        );
    }
}
