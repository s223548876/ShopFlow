package com.shopflow.admin;

import com.shopflow.admin.dto.AdminOrderResponse;
import com.shopflow.admin.dto.UpdateOrderStatusRequest;
import com.shopflow.common.api.PageResponse;
import com.shopflow.common.error.ApiErrorResponse;
import com.shopflow.order.OrderStatus;
import com.shopflow.order.dto.OrderSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin Orders")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED; ADMIN role required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class AdminOrderController {

    private final AdminOrderService orderService;

    public AdminOrderController(AdminOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "List all customer orders")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, INVALID_PAGE_REQUEST or INVALID_SORT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<OrderSummaryResponse> list(
            @Parameter(description = "Order status filter")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", format = "int32",
                    defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") int size,
            @Parameter(schema = @Schema(
                    allowableValues = {"createdAt,asc", "createdAt,desc"},
                    defaultValue = "createdAt,desc"
            ))
            @RequestParam(required = false) String sort
    ) {
        return orderService.list(status, page, size, sort);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get any customer order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AdminOrderResponse get(@PathVariable Long orderId) {
        return orderService.get(orderId);
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "Advance or cancel an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order status updated", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVALID_ORDER_TRANSITION",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AdminOrderResponse updateStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateStatus(orderId, request.status());
    }
}
