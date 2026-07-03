package com.shopflow.cart;

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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CartRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User owner;
    private User otherUser;
    private Product product;

    @BeforeEach
    void setUp() {
        Role customer = roleRepository.save(new Role(RoleName.CUSTOMER));
        owner = userRepository.save(new User(
                "owner@example.com", "password-hash", "Owner", customer
        ));
        otherUser = userRepository.save(new User(
                "other@example.com", "password-hash", "Other", customer
        ));
        Category category = categoryRepository.save(new Category("Electronics"));
        product = productRepository.save(new Product(
                category,
                "Mechanical Keyboard",
                "Hot-swappable keyboard",
                new BigDecimal("89.90"),
                12
        ));
    }

    @Test
    void cartAndItemQueriesAreScopedToOwner() {
        Cart cart = cartRepository.save(new Cart(owner));
        CartItem item = cartItemRepository.save(new CartItem(cart, product, 2));
        cartItemRepository.flush();
        Long itemId = item.getId();
        entityManager.clear();

        Cart loaded = cartRepository.findByUserId(owner.getId()).orElseThrow();

        assertThat(loaded.getItems()).singleElement()
                .extracting(CartItem::getId)
                .isEqualTo(itemId);
        assertThat(cartItemRepository.findByIdAndCartUserId(itemId, owner.getId()))
                .get()
                .extracting(CartItem::getId)
                .isEqualTo(itemId);
        assertThat(cartItemRepository.findByIdAndCartUserId(itemId, otherUser.getId()))
                .isEmpty();
    }

    @Test
    void databaseMappingAllowsOnlyOneCartPerUser() {
        cartRepository.saveAndFlush(new Cart(owner));

        assertThatThrownBy(() -> cartRepository.saveAndFlush(new Cart(owner)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseMappingAllowsOnlyOneItemPerCartAndProduct() {
        Cart cart = cartRepository.save(new Cart(owner));
        cartItemRepository.saveAndFlush(new CartItem(cart, product, 1));

        assertThatThrownBy(() -> cartItemRepository.saveAndFlush(new CartItem(cart, product, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
