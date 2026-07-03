package com.shopflow.cart;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException() {
        super("Insufficient stock");
    }
}
