package com.shopflow.admin;

import com.shopflow.admin.dto.AdminOrderResponse;
import com.shopflow.admin.dto.AdminOrderUserResponse;
import com.shopflow.auth.AuthService;
import com.shopflow.auth.UserRepository;
import com.shopflow.cart.CartService;
import com.shopflow.catalog.CatalogService;
import com.shopflow.catalog.InvalidPageRequestException;
import com.shopflow.common.api.PageResponse;
import com.shopflow.common.security.JwtService;
import com.shopflow.order.InvalidOrderSortException;
import com.shopflow.order.InvalidOrderTransitionException;
import com.shopflow.order.OrderNotFoundException;
import com.shopflow.order.OrderService;
import com.shopflow.order.OrderStatus;
import com.shopflow.order.dto.OrderItemResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class AdminOrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AdminOrderService adminOrderService;

    @MockitoBean
    private AdminProductService adminProductService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void missingJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void customerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/orders")
                        .header("Authorization", bearer("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(adminOrderService, never()).list(null, 0, 20, null);
    }

    @Test
    void adminCanListGetAndUpdateOrders() throws Exception {
        PageResponse<OrderSummaryResponse> page = new PageResponse<>(
                List.of(new OrderSummaryResponse(
                        701L, OrderStatus.PAID, new BigDecimal("24.00"), 2,
                        Instant.parse("2026-07-03T10:00:00Z")
                )),
                1, 10, 1, 1, true, true
        );
        AdminOrderResponse paid = response(OrderStatus.PAID);
        AdminOrderResponse processing = response(OrderStatus.PROCESSING);
        when(adminOrderService.list(OrderStatus.PAID, 1, 10, "createdAt,asc")).thenReturn(page);
        when(adminOrderService.get(701L)).thenReturn(paid);
        when(adminOrderService.updateStatus(701L, OrderStatus.PROCESSING)).thenReturn(processing);

        mockMvc.perform(get("/api/admin/orders?status=PAID&page=1&size=10&sort=createdAt,asc")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(701))
                .andExpect(jsonPath("$.content[0].itemCount").value(2));

        mockMvc.perform(get("/api/admin/orders/701")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("customer@example.com"))
                .andExpect(jsonPath("$.items[0].productName").value("Snapshot name"));

        mockMvc.perform(patch("/api/admin/orders/701/status")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        verify(adminOrderService).list(OrderStatus.PAID, 1, 10, "createdAt,asc");
        verify(adminOrderService).get(701L);
        verify(adminOrderService).updateStatus(701L, OrderStatus.PROCESSING);
    }

    @Test
    void listRejectsInvalidPageSortAndStatus() throws Exception {
        when(adminOrderService.list(null, -1, 20, null)).thenThrow(new InvalidPageRequestException());
        when(adminOrderService.list(null, 0, 20, "status,asc")).thenThrow(new InvalidOrderSortException());

        expectListError("page=-1", "INVALID_PAGE_REQUEST");
        expectListError("sort=status,asc", "INVALID_SORT");

        mockMvc.perform(get("/api/admin/orders?status=NOT_A_STATUS")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void statusBodyValidationUsesExistingErrorFormat() throws Exception {
        mockMvc.perform(patch("/api/admin/orders/701/status")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("status"));

        mockMvc.perform(patch("/api/admin/orders/701/status")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void orderErrorsUseDocumentedCodes() throws Exception {
        when(adminOrderService.get(999L)).thenThrow(new OrderNotFoundException());
        when(adminOrderService.updateStatus(999L, OrderStatus.CANCELLED))
                .thenThrow(new OrderNotFoundException());
        when(adminOrderService.updateStatus(701L, OrderStatus.CANCELLED))
                .thenThrow(new InvalidOrderTransitionException());

        mockMvc.perform(get("/api/admin/orders/999")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        expectPatchError(999L, "CANCELLED", 404, "ORDER_NOT_FOUND");
        expectPatchError(701L, "CANCELLED", 409, "INVALID_ORDER_TRANSITION");
    }

    private void expectListError(String query, String code) throws Exception {
        mockMvc.perform(get("/api/admin/orders?" + query)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void expectPatchError(long orderId, String requestedStatus, int status, String code) throws Exception {
        mockMvc.perform(patch("/api/admin/orders/%d/status".formatted(orderId))
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"%s\"}".formatted(requestedStatus)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(101L, role).accessToken();
    }

    private AdminOrderResponse response(OrderStatus status) {
        return new AdminOrderResponse(
                701L,
                new AdminOrderUserResponse(101L, "customer@example.com", "Customer"),
                status,
                new BigDecimal("24.00"),
                Instant.parse("2026-07-03T09:00:00Z"),
                Instant.parse("2026-07-03T08:00:00Z"),
                List.of(new OrderItemResponse(
                        501L, "Snapshot name", new BigDecimal("12.00"), 2, new BigDecimal("24.00")
                ))
        );
    }
}
