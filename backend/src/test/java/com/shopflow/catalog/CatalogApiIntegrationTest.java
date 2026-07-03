package com.shopflow.catalog;

import com.shopflow.admin.AdminProductService;
import com.shopflow.admin.dto.AdminProductResponse;
import com.shopflow.admin.dto.StockResponse;
import com.shopflow.auth.AuthService;
import com.shopflow.auth.UserRepository;
import com.shopflow.catalog.dto.CategoryResponse;
import com.shopflow.catalog.dto.ProductDetailResponse;
import com.shopflow.catalog.dto.ProductSummaryResponse;
import com.shopflow.common.api.PageResponse;
import com.shopflow.cart.CartService;
import com.shopflow.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-that-is-at-least-32-bytes",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class CatalogApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AdminProductService adminProductService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @Test
    void categoriesAndProductsArePublic() throws Exception {
        CategoryResponse category = new CategoryResponse(1L, "Electronics");
        ProductSummaryResponse product = new ProductSummaryResponse(
                501L, "Keyboard", new BigDecimal("89.90"), 12, category
        );
        when(catalogService.getCategories()).thenReturn(List.of(category));
        when(catalogService.searchProducts(null, null, 0, 20, null))
                .thenReturn(new PageResponse<>(List.of(product), 0, 20, 1, 1, true, true));
        when(catalogService.getProduct(501L)).thenReturn(new ProductDetailResponse(
                501L, "Keyboard", "Hot-swappable keyboard", new BigDecimal("89.90"), 12, category
        ));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Electronics"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(501))
                .andExpect(jsonPath("$.content[0].active").doesNotExist());

        mockMvc.perform(get("/api/products/501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Hot-swappable keyboard"))
                .andExpect(jsonPath("$.active").doesNotExist());
    }

    @Test
    void inactivePublicDetailUsesProductNotFoundDto() throws Exception {
        when(catalogService.getProduct(501L)).thenThrow(new ProductNotFoundException());

        mockMvc.perform(get("/api/products/501"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/products/501"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void invalidPageAndSortUseDocumentedBadRequestCodes() throws Exception {
        when(catalogService.searchProducts(null, null, -1, 20, null))
                .thenThrow(new InvalidPageRequestException());
        when(catalogService.searchProducts(null, null, 0, 20, "stock,asc"))
                .thenThrow(new InvalidSortException());

        mockMvc.perform(get("/api/products").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
        mockMvc.perform(get("/api/products").param("sort", "stock,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
        mockMvc.perform(get("/api/products").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
        mockMvc.perform(get("/api/products").param("categoryId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("categoryId"));
    }

    @Test
    void customerCannotUseAdminProducts() throws Exception {
        mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminCanReadCreateUpdateStockAndSoftDeleteProducts() throws Exception {
        AdminProductResponse response = adminProduct(true);
        when(adminProductService.search(null, null, null, 0, 20, null))
                .thenReturn(new PageResponse<>(List.of(response), 0, 20, 1, 1, true, true));
        when(adminProductService.get(501L)).thenReturn(response);
        when(adminProductService.create(any())).thenReturn(response);
        when(adminProductService.update(eq(501L), any())).thenReturn(response);
        when(adminProductService.updateStock(501L, 25))
                .thenReturn(new StockResponse(501L, 25, response.updatedAt()));

        mockMvc.perform(get("/api/admin/products")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].active").value(true));

        mockMvc.perform(get("/api/admin/products/501")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("89.90", 12)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(501));

        mockMvc.perform(put("/api/admin/products/501")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 1,
                                  "name": "Keyboard V2",
                                  "description": "Updated",
                                  "price": 99.90,
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(patch("/api/admin/products/501/stock")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(25));

        mockMvc.perform(delete("/api/admin/products/501")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void priceAndStockValidationUseUniformErrorDto() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("0", -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'price')]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'stock')]").isNotEmpty());

        mockMvc.perform(patch("/api/admin/products/501/stock")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void productUpdateRejectsStockBecauseItHasASeparateEndpoint() throws Exception {
        mockMvc.perform(put("/api/admin/products/501")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": 1,
                                  "name": "Keyboard V2",
                                  "description": "Updated",
                                  "price": 99.90,
                                  "active": true,
                                  "stock": 999
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void missingCategoryUsesCategoryNotFoundDto() throws Exception {
        when(adminProductService.create(any())).thenThrow(new CategoryNotFoundException());

        mockMvc.perform(post("/api/admin/products")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("89.90", 12)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void missingProductDeleteUsesProductNotFoundDto() throws Exception {
        doThrow(new ProductNotFoundException()).when(adminProductService).deactivate(999L);

        mockMvc.perform(delete("/api/admin/products/999")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(101L, role).accessToken();
    }

    private String createBody(String price, int stock) {
        return """
                {
                  "categoryId": 1,
                  "name": "Keyboard",
                  "description": "Hot-swappable keyboard",
                  "price": %s,
                  "stock": %d
                }
                """.formatted(price, stock);
    }

    private AdminProductResponse adminProduct(boolean active) {
        return new AdminProductResponse(
                501L,
                "Keyboard",
                "Hot-swappable keyboard",
                new BigDecimal("89.90"),
                12,
                active,
                new CategoryResponse(1L, "Electronics"),
                Instant.parse("2026-07-01T08:00:00Z"),
                Instant.parse("2026-07-02T08:00:00Z")
        );
    }
}
