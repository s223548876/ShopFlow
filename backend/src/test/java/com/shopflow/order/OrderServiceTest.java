package com.shopflow.order;

import com.shopflow.auth.User;
import com.shopflow.cart.Cart;
import com.shopflow.cart.CartItem;
import com.shopflow.cart.CartRepository;
import com.shopflow.cart.InsufficientStockException;
import com.shopflow.cart.ProductUnavailableException;
import com.shopflow.catalog.Category;
import com.shopflow.catalog.Product;
import com.shopflow.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private User user;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(cartRepository, productRepository, orderRepository);
    }

    @Test
    void createsOrderFromLockedProductsInIdOrderUsingCurrentSnapshots() {
        Cart cart = cartWithItems(
                item(product(20L, "Stale B", "1.00", 99, true), 1),
                item(product(10L, "Stale A", "1.00", 99, true), 2)
        );
        Product lockedA = product(10L, "Current A", "12.00", 5, true);
        Product lockedB = product(20L, "Current B", "7.00", 3, true);
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdInOrderByIdForUpdate(any()))
                .thenReturn(List.of(lockedA, lockedB));
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> savedOrder(invocation.getArgument(0)));

        var response = service.create(101L);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(response.totalAmount()).isEqualByComparingTo("31.00");
        assertThat(response.items()).extracting(item -> item.productId())
                .containsExactly(10L, 20L);
        assertThat(response.items().get(0).productName()).isEqualTo("Current A");
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("12.00");
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("24.00");
        assertThat(lockedA.getStock()).isEqualTo(3);
        assertThat(lockedB.getStock()).isEqualTo(2);
        assertThat(cart.getItems()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(productRepository).findAllByIdInOrderByIdForUpdate(ids.capture());
        assertThat(ids.getValue()).containsExactly(10L, 20L);
        InOrder order = inOrder(cartRepository, productRepository, orderRepository);
        order.verify(cartRepository).findByUserId(101L);
        order.verify(productRepository).findAllByIdInOrderByIdForUpdate(any());
        order.verify(orderRepository).saveAndFlush(any(Order.class));
    }

    @Test
    void rejectsMissingOrEmptyCart() {
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(101L)).isInstanceOf(CartEmptyException.class);

        Cart empty = new Cart(user);
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.of(empty));
        assertThatThrownBy(() -> service.create(101L)).isInstanceOf(CartEmptyException.class);
        verify(productRepository, never()).findAllByIdInOrderByIdForUpdate(any());
    }

    @Test
    void rejectsProductMissingAfterLock() {
        Cart cart = cartWithItems(item(product(10L, "Stale", "1.00", 1, true), 1));
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdInOrderByIdForUpdate(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(101L)).isInstanceOf(ProductUnavailableException.class);
        assertThat(cart.getItems()).hasSize(1);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInactiveProductAfterLock() {
        Cart cart = cartWithItems(item(product(10L, "Stale", "1.00", 1, true), 1));
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdInOrderByIdForUpdate(any()))
                .thenReturn(List.of(product(10L, "Current", "10.00", 1, false)));

        assertThatThrownBy(() -> service.create(101L)).isInstanceOf(ProductUnavailableException.class);
        assertThat(cart.getItems()).hasSize(1);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInsufficientStockBeforeChangingAnyProduct() {
        Cart cart = cartWithItems(
                item(product(10L, "Stale A", "1.00", 99, true), 1),
                item(product(20L, "Stale B", "1.00", 99, true), 2)
        );
        Product enough = product(10L, "Current A", "10.00", 5, true);
        Product insufficient = product(20L, "Current B", "20.00", 1, true);
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdInOrderByIdForUpdate(any()))
                .thenReturn(List.of(enough, insufficient));

        assertThatThrownBy(() -> service.create(101L)).isInstanceOf(InsufficientStockException.class);
        assertThat(enough.getStock()).isEqualTo(5);
        assertThat(insufficient.getStock()).isEqualTo(1);
        assertThat(cart.getItems()).hasSize(2);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void persistenceFailurePropagatesWithoutClearingCart() {
        Cart cart = cartWithItems(item(product(10L, "Stale", "1.00", 99, true), 1));
        Product locked = product(10L, "Current", "10.00", 2, true);
        when(cartRepository.findByUserId(101L)).thenReturn(Optional.of(cart));
        when(productRepository.findAllByIdInOrderByIdForUpdate(any())).thenReturn(List.of(locked));
        when(orderRepository.saveAndFlush(any(Order.class)))
                .thenThrow(new DataIntegrityViolationException("forced failure"));

        assertThatThrownBy(() -> service.create(101L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("forced failure");
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    void listsOwnerOrdersWithQuantitySumAndSnapshotDetail() {
        Order order = savedOrder(new Order(user, new BigDecimal("30.00")));
        order.addItem(product(10L, "Changed product", "999.00", 1, false),
                "Snapshot A", new BigDecimal("10.00"), 1, new BigDecimal("10.00"));
        order.addItem(product(20L, "Changed product", "999.00", 1, false),
                "Snapshot B", new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        when(orderRepository.findByUserId(any(), any())).thenReturn(new PageImpl<>(List.of(order)));
        when(orderRepository.findByIdAndUserId(701L, 101L)).thenReturn(Optional.of(order));

        var page = service.list(101L, 0, 20, null);
        var detail = service.get(101L, 701L);

        assertThat(page.content()).singleElement().satisfies(summary -> {
            assertThat(summary.itemCount()).isEqualTo(3);
            assertThat(summary.totalAmount()).isEqualByComparingTo("30.00");
        });
        assertThat(detail.items()).extracting(item -> item.productName())
                .containsExactly("Snapshot A", "Snapshot B");
        assertThatThrownBy(() -> service.get(202L, 701L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void paysOnlyOwnersPendingOrder() {
        Order pending = savedOrder(new Order(user, new BigDecimal("10.00")));
        when(orderRepository.findByIdAndUserIdForUpdate(701L, 101L)).thenReturn(Optional.of(pending));
        when(orderRepository.saveAndFlush(pending)).thenReturn(pending);

        var response = service.pay(101L, 701L);

        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.paidAt()).isNotNull();

        assertThatThrownBy(() -> service.pay(101L, 701L))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> service.pay(202L, 701L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private Cart cartWithItems(CartItem... items) {
        Cart cart = new Cart(user);
        for (CartItem item : items) {
            ReflectionTestUtils.setField(item, "cart", cart);
            cart.getItems().add(item);
        }
        return cart;
    }

    private CartItem item(Product product, int quantity) {
        return new CartItem(new Cart(user), product, quantity);
    }

    private Product product(long id, String name, String price, int stock, boolean active) {
        Category category = new Category("Category " + id);
        Product product = new Product(category, name, "Description", new BigDecimal(price), stock);
        product.update(category, name, product.getDescription(), product.getPrice(), active);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private Order savedOrder(Order order) {
        ReflectionTestUtils.setField(order, "id", 701L);
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-07-03T10:00:00Z"));
        return order;
    }
}
