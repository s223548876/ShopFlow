package com.shopflow.order.dto;

import com.shopflow.order.Order;
import com.shopflow.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        int itemCount,
        Instant createdAt
) {

    public static OrderSummaryResponse from(Order order) {
        int itemCount = order.getItems().stream().mapToInt(item -> item.getQuantity()).sum();
        return new OrderSummaryResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                itemCount,
                order.getCreatedAt()
        );
    }
}
