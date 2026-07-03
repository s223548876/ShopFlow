package com.shopflow.cart;

import com.shopflow.cart.dto.AddCartItemRequest;
import com.shopflow.cart.dto.CartItemResponse;
import com.shopflow.cart.dto.CartResponse;
import com.shopflow.cart.dto.UpdateCartItemRequest;
import com.shopflow.common.security.AuthenticatedUser;
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
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal AuthenticatedUser user) {
        return cartService.getCart(user.userId());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse addItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return cartService.addItem(user.userId(), request);
    }

    @PatchMapping("/items/{itemId}")
    public CartItemResponse updateItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateItem(user.userId(), itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long itemId
    ) {
        cartService.deleteItem(user.userId(), itemId);
    }
}
