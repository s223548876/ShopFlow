package com.shopflow.cart.dto;

import com.shopflow.cart.Cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        BigDecimal estimatedTotal
) {

    public static CartResponse from(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(CartItemResponse::from)
                .toList();
        BigDecimal estimatedTotal = items.stream()
                .map(CartItemResponse::subtotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        return new CartResponse(cart.getId(), items, estimatedTotal);
    }
}
