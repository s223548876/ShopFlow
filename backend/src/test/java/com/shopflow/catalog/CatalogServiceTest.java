package com.shopflow.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        catalogService = new CatalogService(categoryRepository, productRepository);
    }

    @Test
    void publicSearchAlwaysRequestsActiveProductsAndMapsPageResponse() {
        Product product = product(true);
        when(productRepository.search(eq(true), eq(1L), eq("keyboard"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        var response = catalogService.searchProducts(" keyboard ", 1L, 0, 20, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).search(eq(true), eq(1L), eq("keyboard"), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(501L);
            assertThat(item.category().name()).isEqualTo("Electronics");
        });
    }

    @Test
    void inactiveOrMissingProductUsesProductNotFound() {
        when(productRepository.findByIdAndActiveTrue(501L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.getProduct(501L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void categoriesAreReturnedAsDtos() {
        Category category = category();
        when(categoryRepository.findAllByOrderByIdAsc()).thenReturn(List.of(category));

        assertThat(catalogService.getCategories())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(1L);
                    assertThat(item.name()).isEqualTo("Electronics");
                });
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
        ReflectionTestUtils.setField(product, "id", 501L);
        ReflectionTestUtils.setField(product, "createdAt", Instant.parse("2026-07-01T08:00:00Z"));
        ReflectionTestUtils.setField(product, "updatedAt", Instant.parse("2026-07-02T08:00:00Z"));
        return product;
    }

    private Category category() {
        Category category = new Category("Electronics");
        ReflectionTestUtils.setField(category, "id", 1L);
        return category;
    }
}
