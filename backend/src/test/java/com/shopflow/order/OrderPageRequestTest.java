package com.shopflow.order;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPageRequestTest {

    @Test
    void defaultsToCreatedAtDescending() {
        var pageable = OrderPageRequest.of(0, 20, null);

        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void acceptsOnlyCreatedAtSort() {
        assertThat(OrderPageRequest.of(0, 20, "createdAt,asc").getSort()
                .getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThatThrownBy(() -> OrderPageRequest.of(0, 20, "totalAmount,desc"))
                .isInstanceOf(InvalidOrderSortException.class);
        assertThatThrownBy(() -> OrderPageRequest.of(0, 20, "createdAt,sideways"))
                .isInstanceOf(InvalidOrderSortException.class);
    }

    @Test
    void validatesPageAndSize() {
        assertThatThrownBy(() -> OrderPageRequest.of(-1, 20, null))
                .isInstanceOf(com.shopflow.catalog.InvalidPageRequestException.class);
        assertThatThrownBy(() -> OrderPageRequest.of(0, 101, null))
                .isInstanceOf(com.shopflow.catalog.InvalidPageRequestException.class);
    }
}
