package com.shopflow.admin;

import com.shopflow.admin.dto.AdminProductResponse;
import com.shopflow.admin.dto.CreateProductRequest;
import com.shopflow.admin.dto.StockResponse;
import com.shopflow.admin.dto.UpdateProductRequest;
import com.shopflow.admin.dto.UpdateStockRequest;
import com.shopflow.common.api.PageResponse;
import com.shopflow.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
@Tag(name = "Admin Products")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "AUTHENTICATION_REQUIRED",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "ACCESS_DENIED; ADMIN role required",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class AdminProductController {

    private final AdminProductService productService;

    public AdminProductController(AdminProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Search all products, including inactive products")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "INVALID_PAGE_REQUEST, INVALID_SORT or VALIDATION_ERROR",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PageResponse<AdminProductResponse> search(
            @Parameter(description = "Case-insensitive product name or description keyword")
            @RequestParam(required = false) String q,
            @Parameter(description = "Category ID")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Filter by active state; omit to include both")
            @RequestParam(required = false) Boolean active,
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
        return productService.search(q, categoryId, active, page, size, sort);
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get a product, including an inactive product")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product returned", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AdminProductResponse get(@PathVariable Long productId) {
        return productService.get(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an active product")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "CATEGORY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AdminProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Update product details and active state")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND or CATEGORY_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AdminProductResponse update(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.update(productId, request);
    }

    @PatchMapping("/{productId}/stock")
    @Operation(summary = "Set absolute product stock")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock updated", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR or MALFORMED_REQUEST",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public StockResponse updateStock(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateStockRequest request
    ) {
        return productService.updateStock(productId, request.quantity());
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a product")
    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public void deactivate(@PathVariable Long productId) {
        productService.deactivate(productId);
    }
}
