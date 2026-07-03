package com.shopflow.cart;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    boolean existsByCartIdAndProductId(Long cartId, Long productId);

    @EntityGraph(attributePaths = {"cart", "product"})
    Optional<CartItem> findByIdAndCartUserId(Long itemId, Long userId);
}
