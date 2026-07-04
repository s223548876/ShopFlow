package com.shopflow.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(requiredProperties = {"productId", "stock", "updatedAt"})
public record StockResponse(Long productId, int stock, Instant updatedAt) {
}
