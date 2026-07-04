package com.shopflow.admin;

import com.shopflow.admin.dto.AdminOrderResponse;
import com.shopflow.admin.dto.UpdateOrderStatusRequest;
import com.shopflow.common.api.PageResponse;
import com.shopflow.order.OrderStatus;
import com.shopflow.order.dto.OrderSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService orderService;

    public AdminOrderController(AdminOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return orderService.list(status, page, size, sort);
    }

    @GetMapping("/{orderId}")
    public AdminOrderResponse get(@PathVariable Long orderId) {
        return orderService.get(orderId);
    }

    @PatchMapping("/{orderId}/status")
    public AdminOrderResponse updateStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateStatus(orderId, request.status());
    }
}
