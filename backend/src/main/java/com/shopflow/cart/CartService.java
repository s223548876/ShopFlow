package com.shopflow.cart;

import com.shopflow.auth.UserRepository;
import com.shopflow.cart.dto.AddCartItemRequest;
import com.shopflow.cart.dto.CartItemResponse;
import com.shopflow.cart.dto.CartResponse;
import com.shopflow.catalog.Product;
import com.shopflow.catalog.ProductNotFoundException;
import com.shopflow.catalog.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public CartResponse getCart(long userId) {
        return CartResponse.from(findOrCreateCart(userId));
    }

    public CartItemResponse addItem(long userId, AddCartItemRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(ProductNotFoundException::new);
        validateAvailable(product, request.quantity());

        Cart cart = findOrCreateCart(userId);
        if (cartItemRepository.existsByCartIdAndProductId(cart.getId(), product.getId())) {
            throw new CartItemAlreadyExistsException();
        }

        try {
            CartItem item = cartItemRepository.saveAndFlush(
                    new CartItem(cart, product, request.quantity())
            );
            return CartItemResponse.from(item);
        } catch (DataIntegrityViolationException exception) {
            throw new CartItemAlreadyExistsException();
        }
    }

    public CartItemResponse updateItem(long userId, long itemId, int quantity) {
        CartItem item = findOwnedItem(userId, itemId);
        validateAvailable(item.getProduct(), quantity);
        item.updateQuantity(quantity);
        return CartItemResponse.from(cartItemRepository.saveAndFlush(item));
    }

    public void deleteItem(long userId, long itemId) {
        cartItemRepository.delete(findOwnedItem(userId, itemId));
    }

    private Cart findOrCreateCart(long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.saveAndFlush(
                        new Cart(userRepository.getReferenceById(userId))
                ));
    }

    private CartItem findOwnedItem(long userId, long itemId) {
        return cartItemRepository.findByIdAndCartUserId(itemId, userId)
                .orElseThrow(CartItemNotFoundException::new);
    }

    private void validateAvailable(Product product, int quantity) {
        if (!product.isActive()) {
            throw new ProductUnavailableException();
        }
        if (quantity > product.getStock()) {
            throw new InsufficientStockException();
        }
    }
}
