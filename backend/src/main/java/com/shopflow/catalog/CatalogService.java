package com.shopflow.catalog;

import com.shopflow.catalog.dto.CategoryResponse;
import com.shopflow.catalog.dto.ProductDetailResponse;
import com.shopflow.catalog.dto.ProductSummaryResponse;
import com.shopflow.common.api.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CatalogService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderByIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public PageResponse<ProductSummaryResponse> searchProducts(
            String q,
            Long categoryId,
            int page,
            int size,
            String sort
    ) {
        var products = productRepository.search(
                true,
                categoryId,
                normalizeQuery(q),
                ProductPageRequest.of(page, size, sort)
        );
        return PageResponse.from(products.map(ProductSummaryResponse::from));
    }

    public ProductDetailResponse getProduct(Long productId) {
        return productRepository.findByIdAndActiveTrue(productId)
                .map(ProductDetailResponse::from)
                .orElseThrow(ProductNotFoundException::new);
    }

    static String normalizeQuery(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }
}
