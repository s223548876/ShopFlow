package com.shopflow.cart;

public class CartItemAlreadyExistsException extends RuntimeException {

    public CartItemAlreadyExistsException() {
        super("Product is already in the cart");
    }
}
