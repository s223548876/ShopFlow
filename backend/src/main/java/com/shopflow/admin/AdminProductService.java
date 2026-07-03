package com.shopflow.admin;

import com.shopflow.admin.dto.AdminProductResponse;
import com.shopflow.admin.dto.CreateProductRequest;
import com.shopflow.admin.dto.StockResponse;
import com.shopflow.admin.dto.UpdateProductRequest;
import com.shopflow.catalog.Category;
import com.shopflow.catalog.CategoryNotFoundException;
import com.shopflow.catalog.CategoryRepository;
import com.shopflow.catalog.Product;
import com.shopflow.catalog.ProductNotFoundException;
import com.shopflow.catalog.ProductPageRequest;
import com.shopflow.catalog.ProductRepository;
import com.shopflow.common.api.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminProductService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public AdminProductService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public PageResponse<AdminProductResponse> search(
            String q,
            Long categoryId,
            Boolean active,
            int page,
            int size,
            String sort
    ) {
        var products = productRepository.search(
                active,
                categoryId,
                normalizeQuery(q),
                ProductPageRequest.of(page, size, sort)
        );
        return PageResponse.from(products.map(AdminProductResponse::from));
    }

    public AdminProductResponse get(Long productId) {
        return AdminProductResponse.from(findProduct(productId));
    }

    @Transactional
    public AdminProductResponse create(CreateProductRequest request) {
        Category category = findCategory(request.categoryId());
        Product product = new Product(
                category,
                request.name().trim(),
                request.description(),
                request.price(),
                request.stock()
        );
        return AdminProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public AdminProductResponse update(Long productId, UpdateProductRequest request) {
        Product product = findProduct(productId);
        product.update(
                findCategory(request.categoryId()),
                request.name().trim(),
                request.description(),
                request.price(),
                request.active()
        );
        return AdminProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public StockResponse updateStock(Long productId, int stock) {
        Product product = findProduct(productId);
        product.updateStock(stock);
        Product saved = productRepository.saveAndFlush(product);
        return new StockResponse(saved.getId(), saved.getStock(), saved.getUpdatedAt());
    }

    @Transactional
    public void deactivate(Long productId) {
        findProduct(productId).deactivate();
    }

    private Product findProduct(Long productId) {
        return productRepository.findWithCategoryById(productId)
                .orElseThrow(ProductNotFoundException::new);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(CategoryNotFoundException::new);
    }

    private String normalizeQuery(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }
}
