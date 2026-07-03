package com.shopflow.cart;

import com.shopflow.admin.AdminProductService;
import com.shopflow.auth.AuthService;
import com.shopflow.auth.UserRepository;
import com.shopflow.cart.dto.AddCartItemRequest;
import com.shopflow.cart.dto.CartItemResponse;
import com.shopflow.cart.dto.CartResponse;
import com.shopflow.catalog.CatalogService;
import com.shopflow.catalog.CategoryRepository;
import com.shopflow.catalog.ProductNotFoundException;
import com.shopflow.catalog.ProductRepository;
import com.shopflow.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class CartApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private AdminProductService adminProductService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private CartRepository cartRepository;

    @MockitoBean
    private CartItemRepository cartItemRepository;

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void adminCannotUseCustomerCartApi() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(cartService, never()).getCart(101L);
    }

    @Test
    void customerCanUseAllDocumentedCartEndpointsWithPrincipalUserId() throws Exception {
        CartItemResponse item = itemResponse(2);
        CartResponse cart = new CartResponse(
                301L,
                List.of(item),
                new BigDecimal("179.80")
        );
        when(cartService.getCart(101L)).thenReturn(cart);
        when(cartService.addItem(101L, new AddCartItemRequest(501L, 2))).thenReturn(item);
        when(cartService.updateItem(101L, 401L, 3)).thenReturn(itemResponse(3));

        mockMvc.perform(get("/api/cart")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.estimatedTotal").value(179.80));

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":501,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(501))
                .andExpect(jsonPath("$.currentUnitPrice").value(89.90));

        mockMvc.perform(patch("/api/cart/items/401")
                        .header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));

        mockMvc.perform(delete("/api/cart/items/401")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isNoContent());

        verify(cartService).getCart(101L);
        verify(cartService).deleteItem(101L, 401L);
    }

    @Test
    void quantityOutsideOneTo999UsesValidationError() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":501,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/cart/items/401")
                        .header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
    }

    @Test
    void cartBusinessErrorsUseDocumentedCodes() throws Exception {
        when(cartService.addItem(101L, new AddCartItemRequest(501L, 1)))
                .thenThrow(new ProductNotFoundException());
        when(cartService.addItem(101L, new AddCartItemRequest(502L, 1)))
                .thenThrow(new ProductUnavailableException());
        when(cartService.addItem(101L, new AddCartItemRequest(503L, 2)))
                .thenThrow(new InsufficientStockException());
        when(cartService.addItem(101L, new AddCartItemRequest(504L, 1)))
                .thenThrow(new CartItemAlreadyExistsException());
        when(cartService.updateItem(101L, 999L, 1))
                .thenThrow(new CartItemNotFoundException());

        expectPostError(501L, 1, 404, "PRODUCT_NOT_FOUND");
        expectPostError(502L, 1, 409, "PRODUCT_UNAVAILABLE");
        expectPostError(503L, 2, 409, "INSUFFICIENT_STOCK");
        expectPostError(504L, 1, 409, "CART_ITEM_ALREADY_EXISTS");

        mockMvc.perform(patch("/api/cart/items/999")
                        .header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    void clearCartEndpointIsNotImplemented() throws Exception {
        mockMvc.perform(delete("/api/cart")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    private void expectPostError(long productId, int quantity, int status, String code) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(101L, role).accessToken();
    }

    private CartItemResponse itemResponse(int quantity) {
        BigDecimal price = new BigDecimal("89.90");
        return new CartItemResponse(
                401L,
                501L,
                "Mechanical Keyboard",
                price,
                quantity,
                price.multiply(BigDecimal.valueOf(quantity)),
                true
        );
    }
}
