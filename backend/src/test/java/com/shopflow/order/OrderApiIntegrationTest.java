package com.shopflow.order;

import com.shopflow.admin.AdminProductService;
import com.shopflow.auth.AuthService;
import com.shopflow.auth.UserRepository;
import com.shopflow.cart.CartService;
import com.shopflow.cart.InsufficientStockException;
import com.shopflow.cart.ProductUnavailableException;
import com.shopflow.catalog.CatalogService;
import com.shopflow.common.api.PageResponse;
import com.shopflow.common.security.JwtService;
import com.shopflow.order.dto.OrderItemResponse;
import com.shopflow.order.dto.OrderResponse;
import com.shopflow.order.dto.OrderSummaryResponse;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private AdminProductService adminProductService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void adminCannotUseCustomerOrderApi() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(orderService, never()).list(101L, 0, 20, null);
    }

    @Test
    void customerCanUseAllDocumentedEndpointsWithPrincipalUserId() throws Exception {
        OrderResponse order = orderResponse(OrderStatus.PENDING_PAYMENT, null);
        PageResponse<OrderSummaryResponse> page = new PageResponse<>(
                List.of(new OrderSummaryResponse(
                        701L,
                        OrderStatus.PENDING_PAYMENT,
                        new BigDecimal("179.80"),
                        2,
                        Instant.parse("2026-07-03T10:00:00Z")
                )),
                0, 20, 1, 1, true, true
        );
        when(orderService.create(101L)).thenReturn(order);
        when(orderService.list(101L, 0, 20, null)).thenReturn(page);
        when(orderService.get(101L, 701L)).thenReturn(order);
        when(orderService.pay(101L, 701L)).thenReturn(orderResponse(
                OrderStatus.PAID, Instant.parse("2026-07-03T10:02:00Z")
        ));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"totalAmount\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(179.80))
                .andExpect(jsonPath("$.items[0].productName").value("Snapshot name"));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].itemCount").value(2));

        mockMvc.perform(get("/api/orders/701")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(701));

        mockMvc.perform(post("/api/orders/701/pay")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").value("2026-07-03T10:02:00Z"));

        verify(orderService).create(101L);
        verify(orderService).get(101L, 701L);
        verify(orderService).pay(101L, 701L);
    }

    @Test
    void creationErrorsUseDocumentedCodes() throws Exception {
        when(orderService.create(101L))
                .thenThrow(new CartEmptyException())
                .thenThrow(new ProductUnavailableException())
                .thenThrow(new InsufficientStockException());

        expectCreateError("CART_EMPTY");
        expectCreateError("PRODUCT_UNAVAILABLE");
        expectCreateError("INSUFFICIENT_STOCK");
    }

    @Test
    void ownerAndPaymentErrorsUseDocumentedCodesAndSchema() throws Exception {
        when(orderService.get(101L, 999L)).thenThrow(new OrderNotFoundException());
        when(orderService.pay(101L, 999L)).thenThrow(new OrderNotFoundException());
        when(orderService.pay(101L, 701L)).thenThrow(new InvalidOrderTransitionException());

        mockMvc.perform(get("/api/orders/999")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/orders/999"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isArray());

        expectPayError(999L, 404, "ORDER_NOT_FOUND");
        expectPayError(701L, 409, "INVALID_ORDER_TRANSITION");
    }

    @Test
    void invalidOrderSortUsesOrderSpecificValidationError() throws Exception {
        when(orderService.list(101L, 0, 20, "totalAmount,desc"))
                .thenThrow(new InvalidOrderSortException());

        mockMvc.perform(get("/api/orders?sort=totalAmount,desc")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"))
                .andExpect(jsonPath("$.message").value("Sort must use createdAt with asc or desc direction"));
    }

    private void expectCreateError(String code) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void expectPayError(long orderId, int status, String code) throws Exception {
        mockMvc.perform(post("/api/orders/%d/pay".formatted(orderId))
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(101L, role).accessToken();
    }

    private OrderResponse orderResponse(OrderStatus status, Instant paidAt) {
        return new OrderResponse(
                701L,
                status,
                new BigDecimal("179.80"),
                paidAt,
                Instant.parse("2026-07-03T10:00:00Z"),
                List.of(new OrderItemResponse(
                        501L,
                        "Snapshot name",
                        new BigDecimal("89.90"),
                        2,
                        new BigDecimal("179.80")
                ))
        );
    }
}
