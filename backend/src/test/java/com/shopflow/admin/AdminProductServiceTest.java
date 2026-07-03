package com.shopflow.admin;

import com.shopflow.admin.dto.CreateProductRequest;
import com.shopflow.admin.dto.UpdateProductRequest;
import com.shopflow.catalog.Category;
import com.shopflow.catalog.CategoryNotFoundException;
import com.shopflow.catalog.CategoryRepository;
import com.shopflow.catalog.Product;
import com.shopflow.catalog.ProductNotFoundException;
import com.shopflow.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private AdminProductService service;

    @BeforeEach
    void setUp() {
        service = new AdminProductService(categoryRepository, productRepository);
    }

    @Test
    void createUsesExistingCategoryAndDefaultsToActive() {
        Category category = category();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            setPersistenceFields(product);
            return product;
        });

        var response = service.create(new CreateProductRequest(
                1L,
                "Mechanical Keyboard",
                "Hot-swappable keyboard",
                new BigDecimal("89.90"),
                12
        ));

        assertThat(response.active()).isTrue();
        assertThat(response.category().id()).isEqualTo(1L);
        verify(productRepository).saveAndFlush(any(Product.class));
    }

    @Test
    void adminSearchForwardsNullableFiltersAndIncludesActiveState() {
        Product product = product(false);
        when(productRepository.search(eq(false), eq(1L), eq("keyboard"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        var response = service.search(" keyboard ", 1L, false, 0, 20, null);

        assertThat(response.content()).singleElement()
                .satisfies(item -> assertThat(item.active()).isFalse());
    }

    @Test
    void missingCategoryIsNotFoundAndProductIsNotSaved() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateProductRequest(
                999L,
                "Keyboard",
                "Description",
                new BigDecimal("10.00"),
                1
        ))).isInstanceOf(CategoryNotFoundException.class);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateCanReactivateWithoutChangingStock() {
        Category category = category();
        Product product = product(false);
        int originalStock = product.getStock();
        when(productRepository.findWithCategoryById(501L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        var response = service.update(501L, new UpdateProductRequest(
                1L,
                "Mechanical Keyboard V2",
                "Updated description",
                new BigDecimal("99.90"),
                true
        ));

        assertThat(response.active()).isTrue();
        assertThat(response.stock()).isEqualTo(originalStock);
        assertThat(product.getName()).isEqualTo("Mechanical Keyboard V2");
    }

    @Test
    void stockEndpointSetsAbsoluteStock() {
        Product product = product(true);
        when(productRepository.findWithCategoryById(501L)).thenReturn(Optional.of(product));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        var response = service.updateStock(501L, 25);

        assertThat(product.getStock()).isEqualTo(25);
        assertThat(response.productId()).isEqualTo(501L);
        assertThat(response.stock()).isEqualTo(25);
    }

    @Test
    void deletingActiveOrInactiveProductIsIdempotentButMissingProductIsNotFound() {
        Product product = product(false);
        when(productRepository.findWithCategoryById(501L)).thenReturn(Optional.of(product));
        when(productRepository.findWithCategoryById(999L)).thenReturn(Optional.empty());

        service.deactivate(501L);

        assertThat(product.isActive()).isFalse();
        verify(productRepository, never()).delete(any());
        assertThatThrownBy(() -> service.deactivate(999L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    private Product product(boolean active) {
        Category category = category();
        Product product = new Product(
                category,
                "Mechanical Keyboard",
                "Hot-swappable keyboard",
                new BigDecimal("89.90"),
                12
        );
        product.update(category, product.getName(), product.getDescription(), product.getPrice(), active);
        setPersistenceFields(product);
        return product;
    }

    private Category category() {
        Category category = new Category("Electronics");
        ReflectionTestUtils.setField(category, "id", 1L);
        return category;
    }

    private void setPersistenceFields(Product product) {
        ReflectionTestUtils.setField(product, "id", 501L);
        ReflectionTestUtils.setField(product, "createdAt", Instant.parse("2026-07-01T08:00:00Z"));
        ReflectionTestUtils.setField(product, "updatedAt", Instant.parse("2026-07-02T08:00:00Z"));
    }
}
