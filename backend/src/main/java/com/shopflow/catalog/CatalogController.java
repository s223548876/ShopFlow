package com.shopflow.catalog;

import com.shopflow.catalog.dto.CategoryResponse;
import com.shopflow.catalog.dto.ProductDetailResponse;
import com.shopflow.catalog.dto.ProductSummaryResponse;
import com.shopflow.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/api/categories")
    public List<CategoryResponse> getCategories() {
        return catalogService.getCategories();
    }

    @GetMapping("/api/products")
    public PageResponse<ProductSummaryResponse> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return catalogService.searchProducts(q, categoryId, page, size, sort);
    }

    @GetMapping("/api/products/{productId}")
    public ProductDetailResponse getProduct(@PathVariable Long productId) {
        return catalogService.getProduct(productId);
    }
}
