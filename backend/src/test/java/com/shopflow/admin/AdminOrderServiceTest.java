package com.shopflow.admin;

import com.shopflow.auth.Role;
import com.shopflow.auth.RoleName;
import com.shopflow.auth.User;
import com.shopflow.catalog.Category;
import com.shopflow.catalog.Product;
import com.shopflow.catalog.ProductRepository;
import com.shopflow.order.InvalidOrderTransitionException;
import com.shopflow.order.Order;
import com.shopflow.order.OrderNotFoundException;
import com.shopflow.order.OrderRepository;
import com.shopflow.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    private AdminOrderService service;
    private User customer;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(orderRepository, productRepository);
        customer = new User("customer@example.com", "hash", "Customer", new Role(RoleName.CUSTOMER));
        ReflectionTestUtils.setField(customer, "id", 101L);
    }

    @Test
    void listsAllOrdersByStatusAndReturnsAdminSnapshotDetail() {
        Product product = product(501L, "Current name", 7);
        Order order = order(701L, OrderStatus.PAID);
        order.addItem(product, "Snapshot name", new BigDecimal("12.00"), 2, new BigDecimal("24.00"));
        when(orderRepository.search(eq(OrderStatus.PAID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(orderRepository.findWithUserAndItemsById(701L)).thenReturn(Optional.of(order));

        var page = service.list(OrderStatus.PAID, 0, 20, null);
        var detail = service.get(701L);

        assertThat(page.content()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(701L);
            assertThat(summary.itemCount()).isEqualTo(2);
        });
        assertThat(detail.user().id()).isEqualTo(101L);
        assertThat(detail.user().email()).isEqualTo("customer@example.com");
        assertThat(detail.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Snapshot name");
            assertThat(item.unitPrice()).isEqualByComparingTo("12.00");
        });
        assertThatThrownBy(() -> service.get(999L)).isInstanceOf(OrderNotFoundException.class);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).search(eq(OrderStatus.PAID), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void advancesOnlyTheDocumentedNormalLifecycle() {
        Order order = order(701L, OrderStatus.PAID);
        when(orderRepository.findByIdForUpdate(701L)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        assertThat(service.updateStatus(701L, OrderStatus.PROCESSING).status())
                .isEqualTo(OrderStatus.PROCESSING);
        assertThat(service.updateStatus(701L, OrderStatus.SHIPPED).status())
                .isEqualTo(OrderStatus.SHIPPED);
        assertThat(service.updateStatus(701L, OrderStatus.COMPLETED).status())
                .isEqualTo(OrderStatus.COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING_PAYMENT", "PAID", "PROCESSING"})
    void cancellationRestoresStockOnceForEachAllowedStatus(OrderStatus initialStatus) {
        Product orderProductB = product(20L, "Snapshot B source", 0);
        Product orderProductA = product(10L, "Snapshot A source", 0);
        Product lockedA = product(10L, "Current A", 3);
        Product lockedB = product(20L, "Current B", 5);
        Order order = order(701L, initialStatus);
        order.addItem(orderProductB, "Snapshot B", new BigDecimal("5.00"), 1, new BigDecimal("5.00"));
        order.addItem(orderProductA, "Snapshot A", new BigDecimal("7.00"), 2, new BigDecimal("14.00"));
        when(orderRepository.findByIdForUpdate(701L)).thenReturn(Optional.of(order));
        when(productRepository.findAllByIdInOrderByIdForUpdate(any()))
                .thenReturn(List.of(lockedA, lockedB));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        var response = service.updateStatus(701L, OrderStatus.CANCELLED);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(lockedA.getStock()).isEqualTo(5);
        assertThat(lockedB.getStock()).isEqualTo(6);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(productRepository).findAllByIdInOrderByIdForUpdate(ids.capture());
        assertThat(ids.getValue()).containsExactly(10L, 20L);
        InOrder locksThenSave = inOrder(orderRepository, productRepository);
        locksThenSave.verify(orderRepository).findByIdForUpdate(701L);
        locksThenSave.verify(productRepository).findAllByIdInOrderByIdForUpdate(any());
        locksThenSave.verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void sameStatusReturnsCurrentOrderWithoutSideEffects() {
        Order cancelled = order(701L, OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(701L)).thenReturn(Optional.of(cancelled));

        var response = service.updateStatus(701L, OrderStatus.CANCELLED);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(productRepository, never()).findAllByIdInOrderByIdForUpdate(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsUndocumentedTransitionsWithoutChangingStock() {
        Order pending = order(701L, OrderStatus.PENDING_PAYMENT);
        Order shipped = order(702L, OrderStatus.SHIPPED);
        Order completed = order(703L, OrderStatus.COMPLETED);
        Order cancelled = order(704L, OrderStatus.CANCELLED);
        when(orderRepository.findByIdForUpdate(701L)).thenReturn(Optional.of(pending));
        when(orderRepository.findByIdForUpdate(702L)).thenReturn(Optional.of(shipped));
        when(orderRepository.findByIdForUpdate(703L)).thenReturn(Optional.of(completed));
        when(orderRepository.findByIdForUpdate(704L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.updateStatus(701L, OrderStatus.PAID))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> service.updateStatus(702L, OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> service.updateStatus(703L, OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> service.updateStatus(704L, OrderStatus.PROCESSING))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> service.updateStatus(999L, OrderStatus.CANCELLED))
                .isInstanceOf(OrderNotFoundException.class);
        verify(productRepository, never()).findAllByIdInOrderByIdForUpdate(any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancellationPersistenceFailurePropagatesForTransactionRollback() {
        Product itemProduct = product(10L, "Snapshot source", 0);
        Product locked = product(10L, "Current", 3);
        Order order = order(701L, OrderStatus.PAID);
        order.addItem(itemProduct, "Snapshot", new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        when(orderRepository.findByIdForUpdate(701L)).thenReturn(Optional.of(order));
        when(productRepository.findAllByIdInOrderByIdForUpdate(any())).thenReturn(List.of(locked));
        when(orderRepository.saveAndFlush(order))
                .thenThrow(new DataIntegrityViolationException("forced failure"));

        assertThatThrownBy(() -> service.updateStatus(701L, OrderStatus.CANCELLED))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("forced failure");
    }

    private Order order(long id, OrderStatus status) {
        Order order = new Order(customer, new BigDecimal("24.00"));
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "status", status);
        ReflectionTestUtils.setField(order, "createdAt", Instant.parse("2026-07-03T10:00:00Z"));
        return order;
    }

    private Product product(long id, String name, int stock) {
        Category category = new Category("Category " + id);
        Product product = new Product(category, name, "Description", new BigDecimal("10.00"), stock);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
