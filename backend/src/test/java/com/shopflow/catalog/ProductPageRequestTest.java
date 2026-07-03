package com.shopflow.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductPageRequestTest {

    @Test
    void usesDocumentedDefaultSort() {
        var pageable = ProductPageRequest.of(0, 20, null);

        assertThat(pageable.getSort().getOrderFor("createdAt"))
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void allowsOnlyDocumentedSortFieldsAndDirections() {
        assertThat(ProductPageRequest.of(0, 20, "price,asc").getSort().getOrderFor("price"))
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.ASC);
        assertThatThrownBy(() -> ProductPageRequest.of(0, 20, "stock,asc"))
                .isInstanceOf(InvalidSortException.class);
        assertThatThrownBy(() -> ProductPageRequest.of(0, 20, "price,sideways"))
                .isInstanceOf(InvalidSortException.class);
    }

    @Test
    void rejectsInvalidPageAndSize() {
        assertThatThrownBy(() -> ProductPageRequest.of(-1, 20, null))
                .isInstanceOf(InvalidPageRequestException.class);
        assertThatThrownBy(() -> ProductPageRequest.of(0, 0, null))
                .isInstanceOf(InvalidPageRequestException.class);
        assertThatThrownBy(() -> ProductPageRequest.of(0, 101, null))
                .isInstanceOf(InvalidPageRequestException.class);
    }
}
