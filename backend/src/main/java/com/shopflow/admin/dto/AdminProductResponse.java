package com.shopflow.admin.dto;

import com.shopflow.catalog.Product;
import com.shopflow.catalog.dto.CategoryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(requiredProperties = {
        "id", "name", "description", "price", "stock", "active", "category", "createdAt", "updatedAt"
})
public record AdminProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        boolean active,
        CategoryResponse category,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminProductResponse from(Product product) {
        return new AdminProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.isActive(),
                CategoryResponse.from(product.getCategory()),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
