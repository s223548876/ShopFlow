package com.shopflow.cart;

import com.shopflow.auth.User;
import com.shopflow.auth.UserRepository;
import com.shopflow.cart.dto.AddCartItemRequest;
import com.shopflow.catalog.Category;
import com.shopflow.catalog.Product;
import com.shopflow.catalog.ProductNotFoundException;
import com.shopflow.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(cartRepository, cartItemRepository, productRepository, userRepository);
    }

    @Test
    void getCartCreatesAnEmptyCartForTheAuthenticatedUser() {
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(101L)).thenReturn(user);
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            ReflectionTestUtils.setField(cart, "id", 301L);
            return cart;
        });

        var response = service.getCart(101L);

        assertThat(response.id()).isEqualTo(301L);
        assertThat(response.items()).isEmpty();
        assertThat(response.estimatedTotal()).isEqualByComparingTo("0.00");
        verify(userRepository).getReferenceById(101L);
    }

    @Test
    void cartResponseUsesCurrentBackendPriceAndAvailability() {
        Cart cart = cart(301L);
        Product available = product(501L, "99.90", 5, true);
        Product insufficient = product(502L, "20.00", 1, true);
        cart.getItems().add(item(401L, cart, available, 2));
        cart.getItems().add(item(402L, cart, insufficient, 3));
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.of(cart));

        var response = service.getCart(101L);

        verify(cartRepository).findByUserId(101L);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).currentUnitPrice()).isEqualByComparingTo("99.90");
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("199.80");
        assertThat(response.items().get(0).available()).isTrue();
        assertThat(response.items().get(1).available()).isFalse();
        assertThat(response.estimatedTotal()).isEqualByComparingTo("259.80");
    }

    @Test
    void firstSuccessfulAddCreatesCartWithoutChangingStock() {
        Product product = product(501L, "89.90", 12, true);
        when(productRepository.findById(501L)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(101L)).thenReturn(user);
        when(cartRepository.saveAndFlush(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            ReflectionTestUtils.setField(cart, "id", 301L);
            return cart;
        });
        when(cartItemRepository.existsByCartIdAndProductId(301L, 501L)).thenReturn(false);
        when(cartItemRepository.saveAndFlush(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 401L);
            return item;
        });

        var response = service.addItem(101L, new AddCartItemRequest(501L, 2));

        assertThat(response.id()).isEqualTo(401L);
        assertThat(response.subtotal()).isEqualByComparingTo("179.80");
        assertThat(product.getStock()).isEqualTo(12);
    }

    @Test
    void duplicateProductIsRejectedInsteadOfMerged() {
        Product product = product(501L, "89.90", 12, true);
        Cart cart = cart(301L);
        when(productRepository.findById(501L)).thenReturn(Optional.of(product));
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.existsByCartIdAndProductId(301L, 501L)).thenReturn(true);

        assertThatThrownBy(() -> service.addItem(101L, new AddCartItemRequest(501L, 2)))
                .isInstanceOf(CartItemAlreadyExistsException.class);
        verify(cartItemRepository, never()).saveAndFlush(any());
    }

    @Test
    void addRejectsMissingInactiveAndInsufficientStockProducts() {
        Product inactive = product(502L, "10.00", 10, false);
        Product insufficient = product(503L, "10.00", 1, true);
        when(productRepository.findById(501L)).thenReturn(Optional.empty());
        when(productRepository.findById(502L)).thenReturn(Optional.of(inactive));
        when(productRepository.findById(503L)).thenReturn(Optional.of(insufficient));

        assertThatThrownBy(() -> service.addItem(101L, new AddCartItemRequest(501L, 1)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.addItem(101L, new AddCartItemRequest(502L, 1)))
                .isInstanceOf(ProductUnavailableException.class);
        assertThatThrownBy(() -> service.addItem(101L, new AddCartItemRequest(503L, 2)))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void updateQuantityIsScopedToOwnerAndRevalidatesProduct() {
        Product product = product(501L, "89.90", 5, true);
        Cart cart = cart(301L);
        CartItem item = item(401L, cart, product, 1);
        when(cartItemRepository.findByIdAndCartUserId(401L, 101L)).thenReturn(Optional.of(item));
        when(cartItemRepository.saveAndFlush(item)).thenReturn(item);

        var response = service.updateItem(101L, 401L, 3);

        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(response.subtotal()).isEqualByComparingTo("269.70");

        assertThatThrownBy(() -> service.updateItem(202L, 401L, 1))
                .isInstanceOf(CartItemNotFoundException.class);
        assertThatThrownBy(() -> service.updateItem(101L, 401L, 6))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void updateRejectsProductThatBecameInactive() {
        Product product = product(501L, "89.90", 5, false);
        CartItem item = item(401L, cart(301L), product, 1);
        when(cartItemRepository.findByIdAndCartUserId(401L, 101L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.updateItem(101L, 401L, 2))
                .isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    void deleteUsesOwnerScopedQuery() {
        CartItem item = item(401L, cart(301L), product(501L, "89.90", 5, true), 1);
        when(cartItemRepository.findByIdAndCartUserId(401L, 101L)).thenReturn(Optional.of(item));

        service.deleteItem(101L, 401L);

        verify(cartItemRepository).delete(item);
        assertThatThrownBy(() -> service.deleteItem(202L, 401L))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    private Cart cart(long id) {
        Cart cart = new Cart(user);
        ReflectionTestUtils.setField(cart, "id", id);
        return cart;
    }

    private Product product(long id, String price, int stock, boolean active) {
        Category category = new Category("Electronics");
        Product product = new Product(
                category,
                "Mechanical Keyboard",
                "Hot-swappable keyboard",
                new BigDecimal(price),
                stock
        );
        product.update(category, product.getName(), product.getDescription(), product.getPrice(), active);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private CartItem item(long id, Cart cart, Product product, int quantity) {
        CartItem item = new CartItem(cart, product, quantity);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }
}
