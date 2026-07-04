package com.shopflow.order;

import com.shopflow.common.api.PageResponse;
import com.shopflow.common.error.ApiErrorResponse;
import com.shopflow.common.security.AuthenticatedUser;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.dto.OrderSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Customer Orders")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED; CUSTOMER role required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an order from the authenticated customer's cart")
    @ApiResponse(responseCode = "409",
            description = "CART_EMPTY, PRODUCT_UNAVAILABLE or INSUFFICIENT_STOCK",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public OrderResponse create(@AuthenticationPrincipal AuthenticatedUser user) {
        return orderService.create(user.userId());
    }

    @GetMapping
    @Operation(summary = "List the authenticated customer's orders")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "INVALID_PAGE_REQUEST or INVALID_SORT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<OrderSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
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
        return orderService.list(user.userId(), page, size, sort);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get an owned order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OrderResponse get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long orderId
    ) {
        return orderService.get(user.userId(), orderId);
    }

    @PostMapping("/{orderId}/pay")
    @Operation(summary = "Simulate successful payment for an owned pending order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order paid", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "ORDER_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "INVALID_ORDER_TRANSITION",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OrderResponse pay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long orderId
    ) {
        return orderService.pay(user.userId(), orderId);
    }
}
