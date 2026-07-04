package com.shopflow.order;

import com.shopflow.auth.Role;
import com.shopflow.auth.RoleName;
import com.shopflow.auth.RoleRepository;
import com.shopflow.auth.User;
import com.shopflow.auth.UserRepository;
import com.shopflow.catalog.Category;
import com.shopflow.catalog.CategoryRepository;
import com.shopflow.catalog.Product;
import com.shopflow.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OrderRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User owner;
    private User otherUser;
    private Product firstProduct;
    private Product secondProduct;

    @BeforeEach
    void setUp() {
        Role customer = roleRepository.save(new Role(RoleName.CUSTOMER));
        owner = userRepository.save(new User("owner@example.com", "hash", "Owner", customer));
        otherUser = userRepository.save(new User("other@example.com", "hash", "Other", customer));
        Category category = categoryRepository.save(new Category("Books"));
        firstProduct = productRepository.save(new Product(
                category, "First", "First product", new BigDecimal("10.00"), 10
        ));
        secondProduct = productRepository.save(new Product(
                category, "Second", "Second product", new BigDecimal("20.00"), 10
        ));
    }

    @Test
    void mapsSnapshotItemsAndScopesDetailToOwner() {
        Order order = new Order(owner, new BigDecimal("20.00"));
        order.addItem(firstProduct, "Original name", new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        order = orderRepository.saveAndFlush(order);

        Order loaded = orderRepository.findByIdAndUserId(order.getId(), owner.getId()).orElseThrow();

        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(loaded.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo(firstProduct.getId());
            assertThat(item.getProductName()).isEqualTo("Original name");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("10.00");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getSubtotal()).isEqualByComparingTo("20.00");
        });
        assertThat(orderRepository.findByIdAndUserId(order.getId(), otherUser.getId())).isEmpty();
    }

    @Test
    void listsOnlyOwnersOrdersAndLocksOwnerOrderForPayment() {
        Order order = orderRepository.saveAndFlush(new Order(owner, new BigDecimal("10.00")));
        orderRepository.saveAndFlush(new Order(otherUser, new BigDecimal("20.00")));

        assertThat(orderRepository.findByUserId(owner.getId(), PageRequest.of(0, 20)).getContent())
                .extracting(Order::getId)
                .containsExactly(order.getId());
        assertThat(orderRepository.findByIdAndUserIdForUpdate(order.getId(), owner.getId())).isPresent();
        assertThat(orderRepository.findByIdAndUserIdForUpdate(order.getId(), otherUser.getId())).isEmpty();
    }

    @Test
    void productLockQueryReturnsProductsInAscendingIdOrder() {
        List<Product> products = productRepository.findAllByIdInOrderByIdForUpdate(
                List.of(secondProduct.getId(), firstProduct.getId())
        );

        assertThat(products).extracting(Product::getId)
                .containsExactly(firstProduct.getId(), secondProduct.getId());
    }

    @Test
    void paymentPersistsStatusAndPaidAt() {
        Order order = new Order(owner, new BigDecimal("10.00"));
        Instant paidAt = Instant.parse("2026-07-03T10:15:30Z");
        order.pay(paidAt);

        Order saved = orderRepository.saveAndFlush(order);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(saved.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    void adminSearchFiltersAllUsersOrdersByStatus() {
        Order pending = orderRepository.saveAndFlush(new Order(owner, new BigDecimal("10.00")));
        Order paid = new Order(otherUser, new BigDecimal("20.00"));
        paid.pay(Instant.parse("2026-07-03T10:15:30Z"));
        paid = orderRepository.saveAndFlush(paid);

        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(orderRepository.search(null, pageable).getContent())
                .extracting(Order::getId)
                .containsExactlyInAnyOrder(pending.getId(), paid.getId());
        assertThat(orderRepository.search(OrderStatus.PAID, pageable).getContent())
                .extracting(Order::getId)
                .containsExactly(paid.getId());
    }

    @Test
    void adminDetailAndWriteLockAreNotOwnerScoped() {
        Order order = new Order(owner, new BigDecimal("20.00"));
        order.addItem(firstProduct, "Snapshot", new BigDecimal("10.00"), 2, new BigDecimal("20.00"));
        order = orderRepository.saveAndFlush(order);

        Order detail = orderRepository.findWithUserAndItemsById(order.getId()).orElseThrow();

        assertThat(detail.getUser().getEmail()).isEqualTo("owner@example.com");
        assertThat(detail.getItems()).singleElement()
                .extracting(OrderItem::getProductName)
                .isEqualTo("Snapshot");
        assertThat(orderRepository.findByIdForUpdate(order.getId())).isPresent();
        assertThat(orderRepository.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
    }
}
