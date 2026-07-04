package com.shopflow.order.dto;

import com.shopflow.order.Order;
import com.shopflow.order.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(requiredProperties = {"id", "status", "totalAmount", "paidAt", "createdAt", "items"})
public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        @Schema(nullable = true) Instant paidAt,
        Instant createdAt,
        List<OrderItemResponse> items
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getPaidAt(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
