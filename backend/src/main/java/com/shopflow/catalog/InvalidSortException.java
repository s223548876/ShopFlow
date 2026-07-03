package com.shopflow.catalog;

public class InvalidSortException extends RuntimeException {

    public InvalidSortException() {
        super("Sort must use name, price, or createdAt with asc or desc direction");
    }
}
