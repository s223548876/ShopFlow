package com.shopflow.cart.dto;

import com.shopflow.cart.CartItem;
import com.shopflow.catalog.Product;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal currentUnitPrice,
        int quantity,
        BigDecimal subtotal,
        boolean available
) {

    public static CartItemResponse from(CartItem item) {
        Product product = item.getProduct();
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(
                item.getId(),
                product.getId(),
                product.getName(),
                product.getPrice(),
                item.getQuantity(),
                subtotal,
                product.isActive() && product.getStock() >= item.getQuantity()
        );
    }
}
