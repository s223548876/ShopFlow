package com.shopflow.admin.dto;

import com.shopflow.order.Order;
import com.shopflow.order.OrderStatus;
import com.shopflow.order.dto.OrderItemResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminOrderResponse(
        Long id,
        AdminOrderUserResponse user,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant paidAt,
        Instant createdAt,
        List<OrderItemResponse> items
) {

    public static AdminOrderResponse from(Order order) {
        return new AdminOrderResponse(
                order.getId(),
                AdminOrderUserResponse.from(order.getUser()),
                order.getStatus(),
                order.getTotalAmount(),
                order.getPaidAt(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList()
        );
    }
}
