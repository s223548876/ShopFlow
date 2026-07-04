package com.shopflow.cart;

import com.shopflow.cart.dto.AddCartItemRequest;
import com.shopflow.cart.dto.CartItemResponse;
import com.shopflow.cart.dto.CartResponse;
import com.shopflow.cart.dto.UpdateCartItemRequest;
import com.shopflow.common.error.ApiErrorResponse;
import com.shopflow.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED; CUSTOMER role required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "Get the authenticated customer's cart")
    @ApiResponse(responseCode = "200", description = "Cart returned", useReturnTypeSchema = true)
    public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.getCart(user.userId());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an item to the authenticated customer's cart")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "CART_ITEM_ALREADY_EXISTS, PRODUCT_UNAVAILABLE or INSUFFICIENT_STOCK",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CartItemResponse addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return cartService.addItem(user.userId(), request);
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Update a cart item quantity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart item updated", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CART_ITEM_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "PRODUCT_UNAVAILABLE or INSUFFICIENT_STOCK",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CartItemResponse updateItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateItem(user.userId(), itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a cart item")
    @ApiResponse(responseCode = "404", description = "CART_ITEM_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public void deleteItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long itemId
    ) {
        cartService.deleteItem(user.userId(), itemId);
    }
}
