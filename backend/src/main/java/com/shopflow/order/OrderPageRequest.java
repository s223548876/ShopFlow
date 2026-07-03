package com.shopflow.order;

import com.shopflow.catalog.InvalidPageRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class OrderPageRequest {

    private static final String DEFAULT_SORT = "createdAt,desc";

    private OrderPageRequest() {
    }

    public static Pageable of(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPageRequestException();
        }

        String[] parts = (sort == null || sort.isBlank() ? DEFAULT_SORT : sort).split(",", -1);
        if (parts.length != 2 || !"createdAt".equals(parts[0])) {
            throw new InvalidOrderSortException();
        }

        try {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(parts[1]), "createdAt"));
        } catch (IllegalArgumentException exception) {
            throw new InvalidOrderSortException();
        }
    }
}
