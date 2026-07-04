package com.shopflow.catalog.dto;

import com.shopflow.catalog.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"id", "name"})
public record CategoryResponse(Long id, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
