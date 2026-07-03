package com.shopflow.catalog;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public final class ProductPageRequest {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "price", "createdAt");
    private static final String DEFAULT_SORT = "createdAt,desc";

    private ProductPageRequest() {
    }

    public static Pageable of(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPageRequestException();
        }

        String[] parts = (sort == null || sort.isBlank() ? DEFAULT_SORT : sort).split(",", -1);
        if (parts.length != 2 || !ALLOWED_SORT_FIELDS.contains(parts[0])) {
            throw new InvalidSortException();
        }

        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSortException();
        }
        return PageRequest.of(page, size, Sort.by(direction, parts[0]));
    }
}
