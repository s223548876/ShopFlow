package com.shopflow.cart.dto;

import com.shopflow.cart.CartItem;
import com.shopflow.catalog.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(requiredProperties = {
        "id", "productId", "productName", "currentUnitPrice", "quantity", "subtotal", "available"
})
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
