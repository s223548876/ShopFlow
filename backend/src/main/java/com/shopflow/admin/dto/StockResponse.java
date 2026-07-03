package com.shopflow.admin.dto;

import java.time.Instant;

public record StockResponse(Long productId, int stock, Instant updatedAt) {
}
