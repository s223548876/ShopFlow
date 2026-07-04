package com.shopflow.catalog;

import com.shopflow.catalog.dto.CategoryResponse;
import com.shopflow.catalog.dto.ProductDetailResponse;
import com.shopflow.catalog.dto.ProductSummaryResponse;
import com.shopflow.common.api.PageResponse;
import com.shopflow.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Catalog")
@ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/api/categories")
    @Operation(summary = "List categories")
    @ApiResponse(responseCode = "200", description = "Categories returned", useReturnTypeSchema = true)
    public List<CategoryResponse> getCategories() {
        return catalogService.getCategories();
    }

    @GetMapping("/api/products")
    @Operation(summary = "Search active products")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "INVALID_PAGE_REQUEST, INVALID_SORT or VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<ProductSummaryResponse> searchProducts(
            @Parameter(description = "Case-insensitive product name or description keyword")
            @RequestParam(required = false) String q,
            @Parameter(description = "Category ID")
            @RequestParam(required = false) Long categoryId,
            @Parameter(schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(type = "integer", format = "int32",
                    defaultValue = "20", minimum = "1", maximum = "100"))
            @RequestParam(defaultValue = "20") int size,
            @Parameter(schema = @Schema(
                    allowableValues = {
                            "name,asc", "name,desc", "price,asc", "price,desc",
                            "createdAt,asc", "createdAt,desc"
                    },
                    defaultValue = "createdAt,desc"
            ))
            @RequestParam(required = false) String sort
    ) {
        return catalogService.searchProducts(q, categoryId, page, size, sort);
    }

    @GetMapping("/api/products/{productId}")
    @Operation(summary = "Get an active product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductDetailResponse getProduct(@PathVariable Long productId) {
        return catalogService.getProduct(productId);
    }
}
