package com.shopflow.order;

public class InvalidOrderTransitionException extends RuntimeException {

    public InvalidOrderTransitionException() {
        super("Invalid order status transition");
    }
}
