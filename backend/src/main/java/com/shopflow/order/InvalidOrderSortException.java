package com.shopflow.order;

public class InvalidOrderSortException extends RuntimeException {

    public InvalidOrderSortException() {
        super("Sort must use createdAt with asc or desc direction");
    }
}
