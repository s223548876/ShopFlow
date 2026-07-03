package com.shopflow.order;

import com.shopflow.common.api.PageResponse;
import com.shopflow.common.security.AuthenticatedUser;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.dto.OrderSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.create(user.userId());
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return orderService.list(user.userId(), page, size, sort);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long orderId
    ) {
        return orderService.get(user.userId(), orderId);
    }

    @PostMapping("/{orderId}/pay")
    public OrderResponse pay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long orderId
    ) {
        return orderService.pay(user.userId(), orderId);
    }
}
