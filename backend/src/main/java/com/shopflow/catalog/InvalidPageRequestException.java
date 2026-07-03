package com.shopflow.catalog;

public class InvalidPageRequestException extends RuntimeException {

    public InvalidPageRequestException() {
        super("Page must be non-negative and size must be between 1 and 100");
    }
}
