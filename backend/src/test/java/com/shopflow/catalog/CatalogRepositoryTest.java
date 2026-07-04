package com.shopflow.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CatalogRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private Category electronics;
    private Category books;
    private Product activeKeyboard;
    private Product inactiveKeyboard;

    @BeforeEach
    void setUp() {
        electronics = categoryRepository.save(new Category("Electronics"));
        books = categoryRepository.save(new Category("Books"));
        activeKeyboard = productRepository.save(new Product(
                electronics,
                "Mechanical Keyboard",
                "Hot-swappable keyboard",
                new BigDecimal("89.90"),
                12
        ));
        inactiveKeyboard = productRepository.save(new Product(
                electronics,
                "Office Keyboard",
                "Quiet keyboard",
                new BigDecimal("39.90"),
                4
        ));
        inactiveKeyboard.update(
                electronics,
                inactiveKeyboard.getName(),
                inactiveKeyboard.getDescription(),
                inactiveKeyboard.getPrice(),
                false
        );
        productRepository.save(new Product(
                books,
                "Spring in Action",
                "Java book",
                new BigDecimal("49.90"),
                8
        ));
        productRepository.flush();
    }

    @Test
    void publicSearchReturnsOnlyActiveProducts() {
        var page = productRepository.search(
                true,
                null,
                null,
                PageRequest.of(0, 20, Sort.by("id"))
        );

        assertThat(page.getContent())
                .extracting(Product::getName)
                .containsExactly("Mechanical Keyboard", "Spring in Action");
    }

    @Test
    void publicSearchSupportsCategoryWithoutKeyword() {
        var page = productRepository.search(
                true,
                electronics.getId(),
                null,
                PageRequest.of(0, 20, Sort.by("id"))
        );

        assertThat(page.getContent())
                .extracting(Product::getName)
                .containsExactly("Mechanical Keyboard");
    }

    @Test
    void publicSearchSupportsKeywordWithoutCategory() {
        var page = productRepository.search(
                true,
                null,
                "SPRING",
                PageRequest.of(0, 20, Sort.by("id"))
        );

        assertThat(page.getContent())
                .extracting(Product::getName)
                .containsExactly("Spring in Action");
    }

    @Test
    void nullableFiltersSupportAdminCategoryAndCaseInsensitiveKeywordSearch() {
        var all = productRepository.search(
                null,
                null,
                null,
                PageRequest.of(0, 20, Sort.by("id"))
        );
        var filtered = productRepository.search(
                null,
                electronics.getId(),
                "KEYBOARD",
                PageRequest.of(0, 20, Sort.by("id"))
        );
        var descriptionMatch = productRepository.search(
                true,
                books.getId(),
                "JAVA",
                PageRequest.of(0, 20, Sort.by("id"))
        );
        var inactive = productRepository.search(
                false,
                null,
                null,
                PageRequest.of(0, 20, Sort.by("id"))
        );

        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(filtered.getContent())
                .extracting(Product::getName)
                .containsExactly("Mechanical Keyboard", "Office Keyboard");
        assertThat(descriptionMatch.getContent())
                .extracting(Product::getName)
                .containsExactly("Spring in Action");
        assertThat(inactive.getContent())
                .extracting(Product::getName)
                .containsExactly("Office Keyboard");
    }

    @Test
    void inactiveProductIsHiddenFromPublicDetailButAvailableToAdmin() {
        assertThat(productRepository.findByIdAndActiveTrue(inactiveKeyboard.getId())).isEmpty();
        assertThat(productRepository.findWithCategoryById(inactiveKeyboard.getId()))
                .contains(inactiveKeyboard);
    }

    @Test
    void categoriesAreMappedAndReturnedInIdOrder() {
        assertThat(categoryRepository.findAllByOrderByIdAsc())
                .extracting(Category::getName)
                .containsExactly("Electronics", "Books");
    }
}
