package com.shopflow.cart;

public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException() {
        super("Product is unavailable");
    }
}
