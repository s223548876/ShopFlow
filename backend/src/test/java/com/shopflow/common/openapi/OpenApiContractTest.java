package com.shopflow.common.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.admin.AdminOrderService;
import com.shopflow.admin.AdminProductService;
import com.shopflow.auth.AuthService;
import com.shopflow.auth.UserRepository;
import com.shopflow.cart.CartService;
import com.shopflow.catalog.CatalogService;
import com.shopflow.order.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private AdminProductService adminProductService;

    @MockitoBean
    private AdminOrderService adminOrderService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private OrderService orderService;

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void openApiJsonIsPublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    void unexpectedErrorsUseTheUniformSchemaWithoutLeakingInternals() throws Exception {
        when(catalogService.getCategories()).thenThrow(new RuntimeException("sensitive internal detail"));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/api/categories"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sensitive internal detail")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("RuntimeException")
                )));
    }

    @Test
    void documentsExactlyTheSupportedOperationsAndTags() throws Exception {
        JsonNode document = openApi();
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("/api/auth/register", Set.of("post"));
        expected.put("/api/auth/login", Set.of("post"));
        expected.put("/api/categories", Set.of("get"));
        expected.put("/api/products", Set.of("get"));
        expected.put("/api/products/{productId}", Set.of("get"));
        expected.put("/api/cart", Set.of("get"));
        expected.put("/api/cart/items", Set.of("post"));
        expected.put("/api/cart/items/{itemId}", Set.of("patch", "delete"));
        expected.put("/api/orders", Set.of("get", "post"));
        expected.put("/api/orders/{orderId}", Set.of("get"));
        expected.put("/api/orders/{orderId}/pay", Set.of("post"));
        expected.put("/api/admin/products", Set.of("get", "post"));
        expected.put("/api/admin/products/{productId}", Set.of("get", "put", "delete"));
        expected.put("/api/admin/products/{productId}/stock", Set.of("patch"));
        expected.put("/api/admin/orders", Set.of("get"));
        expected.put("/api/admin/orders/{orderId}", Set.of("get"));
        expected.put("/api/admin/orders/{orderId}/status", Set.of("patch"));
        expected.put("/actuator/health", Set.of("get"));

        JsonNode paths = document.path("paths");
        assertThat(fieldNames(paths)).containsExactlyInAnyOrderElementsOf(expected.keySet());
        expected.forEach((path, methods) ->
                assertThat(fieldNames(paths.path(path))).containsExactlyInAnyOrderElementsOf(methods));

        assertThat(document.path("tags").findValuesAsText("name"))
                .containsExactly(
                        "Authentication",
                        "Catalog",
                        "Cart",
                        "Customer Orders",
                        "Admin Products",
                        "Admin Orders",
                        "Health"
                );
    }

    @Test
    void documentsBearerSecurityWithoutMarkingPublicOperationsAsProtected() throws Exception {
        JsonNode document = openApi();
        JsonNode scheme = document.at("/components/securitySchemes/bearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");

        for (String operation : List.of(
                "/paths/~1api~1auth~1register/post",
                "/paths/~1api~1auth~1login/post",
                "/paths/~1api~1categories/get",
                "/paths/~1api~1products/get",
                "/paths/~1api~1products~1{productId}/get",
                "/paths/~1actuator~1health/get"
        )) {
            assertThat(document.at(operation).has("security")).isFalse();
        }

        assertProtected(document, "/paths/~1api~1cart/get", "CUSTOMER");
        assertProtected(document, "/paths/~1api~1orders/get", "CUSTOMER");
        assertProtected(document, "/paths/~1api~1admin~1products/get", "ADMIN");
        assertProtected(document, "/paths/~1api~1admin~1orders/get", "ADMIN");
    }

    @Test
    void documentsResponseCodesForEveryOperation() throws Exception {
        JsonNode document = openApi();
        Map<String, Set<String>> expected = Map.ofEntries(
                entry("post", "/api/auth/register", "201", "400", "409", "500"),
                entry("post", "/api/auth/login", "200", "400", "401", "500"),
                entry("get", "/api/categories", "200", "500"),
                entry("get", "/api/products", "200", "400", "500"),
                entry("get", "/api/products/{productId}", "200", "404", "500"),
                entry("get", "/api/cart", "200", "401", "403", "500"),
                entry("post", "/api/cart/items", "201", "400", "401", "403", "404", "409", "500"),
                entry("patch", "/api/cart/items/{itemId}", "200", "400", "401", "403", "404", "409", "500"),
                entry("delete", "/api/cart/items/{itemId}", "204", "401", "403", "404", "500"),
                entry("post", "/api/orders", "201", "401", "403", "409", "500"),
                entry("get", "/api/orders", "200", "400", "401", "403", "500"),
                entry("get", "/api/orders/{orderId}", "200", "401", "403", "404", "500"),
                entry("post", "/api/orders/{orderId}/pay", "200", "401", "403", "404", "409", "500"),
                entry("get", "/api/admin/products", "200", "400", "401", "403", "500"),
                entry("get", "/api/admin/products/{productId}", "200", "401", "403", "404", "500"),
                entry("post", "/api/admin/products", "201", "400", "401", "403", "404", "500"),
                entry("put", "/api/admin/products/{productId}", "200", "400", "401", "403", "404", "500"),
                entry("patch", "/api/admin/products/{productId}/stock", "200", "400", "401", "403", "404", "500"),
                entry("delete", "/api/admin/products/{productId}", "204", "401", "403", "404", "500"),
                entry("get", "/api/admin/orders", "200", "400", "401", "403", "500"),
                entry("get", "/api/admin/orders/{orderId}", "200", "401", "403", "404", "500"),
                entry("patch", "/api/admin/orders/{orderId}/status", "200", "400", "401", "403", "404", "409", "500"),
                entry("get", "/actuator/health", "200", "503")
        );

        expected.forEach((key, statuses) -> {
            String[] parts = key.split(" ", 2);
            assertThat(fieldNames(document.path("paths").path(parts[1]).path(parts[0]).path("responses")))
                    .as(key)
                    .containsExactlyInAnyOrderElementsOf(statuses);
        });

        assertThat(fieldNames(document.at("/components/schemas/ApiErrorResponse/properties")))
                .containsExactlyInAnyOrder(
                        "timestamp", "status", "code", "message", "path", "fieldErrors"
                );
    }

    @Test
    void documentsValidationPaginationFiltersAndSorting() throws Exception {
        JsonNode document = openApi();

        JsonNode register = document.at("/components/schemas/RegisterRequest");
        assertThat(textValues(register.path("required")))
                .containsExactlyInAnyOrder("email", "password", "displayName");
        assertThat(register.at("/properties/password/minLength").asInt()).isEqualTo(8);
        assertThat(register.at("/properties/password/maxLength").asInt()).isEqualTo(72);

        JsonNode addItem = document.at("/components/schemas/AddCartItemRequest");
        assertThat(addItem.at("/properties/quantity/minimum").asInt()).isEqualTo(1);
        assertThat(addItem.at("/properties/quantity/maximum").asInt()).isEqualTo(999);

        assertPageParameters(document, "/api/products");
        assertParameterValues(document, "/api/products", "sort", Set.of(
                "name,asc", "name,desc", "price,asc", "price,desc",
                "createdAt,asc", "createdAt,desc"
        ));
        assertPageParameters(document, "/api/orders");
        assertParameterValues(document, "/api/orders", "sort", Set.of("createdAt,asc", "createdAt,desc"));
        assertPageParameters(document, "/api/admin/products");
        assertPageParameters(document, "/api/admin/orders");
        assertParameterValues(document, "/api/admin/orders", "status", Set.of(
                "PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "COMPLETED", "CANCELLED"
        ));
    }

    @Test
    void documentsRequiredResponseFieldsAndNullablePaymentTime() throws Exception {
        JsonNode schemas = openApi().at("/components/schemas");
        Map<String, Set<String>> expected = Map.ofEntries(
                Map.entry("RegisterResponse", Set.of("id", "email", "displayName", "role", "createdAt")),
                Map.entry("AuthResponse", Set.of("accessToken", "tokenType", "expiresIn")),
                Map.entry("CategoryResponse", Set.of("id", "name")),
                Map.entry("ProductSummaryResponse", Set.of("id", "name", "price", "stock", "category")),
                Map.entry("ProductDetailResponse", Set.of("id", "name", "description", "price", "stock", "category")),
                Map.entry("CartItemResponse", Set.of(
                        "id", "productId", "productName", "currentUnitPrice", "quantity", "subtotal", "available"
                )),
                Map.entry("CartResponse", Set.of("id", "items", "estimatedTotal")),
                Map.entry("OrderItemResponse", Set.of("productId", "productName", "unitPrice", "quantity", "subtotal")),
                Map.entry("OrderSummaryResponse", Set.of("id", "status", "totalAmount", "itemCount", "createdAt")),
                Map.entry("OrderResponse", Set.of("id", "status", "totalAmount", "paidAt", "createdAt", "items")),
                Map.entry("AdminProductResponse", Set.of(
                        "id", "name", "description", "price", "stock", "active", "category", "createdAt", "updatedAt"
                )),
                Map.entry("StockResponse", Set.of("productId", "stock", "updatedAt")),
                Map.entry("AdminOrderUserResponse", Set.of("id", "email", "displayName")),
                Map.entry("AdminOrderResponse", Set.of(
                        "id", "user", "status", "totalAmount", "paidAt", "createdAt", "items"
                )),
                Map.entry("PageResponseProductSummaryResponse", Set.of(
                        "content", "page", "size", "totalElements", "totalPages", "first", "last"
                )),
                Map.entry("PageResponseAdminProductResponse", Set.of(
                        "content", "page", "size", "totalElements", "totalPages", "first", "last"
                )),
                Map.entry("PageResponseOrderSummaryResponse", Set.of(
                        "content", "page", "size", "totalElements", "totalPages", "first", "last"
                )),
                Map.entry("ApiErrorResponse", Set.of(
                        "timestamp", "status", "code", "message", "path", "fieldErrors"
                )),
                Map.entry("FieldErrorResponse", Set.of("field", "message"))
        );

        expected.forEach((schema, fields) -> assertThat(textValues(schemas.path(schema).path("required")))
                .as(schema)
                .containsExactlyInAnyOrderElementsOf(fields));

        assertNullable(schemas.at("/OrderResponse/properties/paidAt"));
        assertNullable(schemas.at("/AdminOrderResponse/properties/paidAt"));
    }

    @Test
    void documentsJsonMediaTypesForApiResponses() throws Exception {
        JsonNode document = openApi();

        assertJsonContent(document, "/api/products", "get", "200");
        assertJsonContent(document, "/api/products", "get", "400");
        assertJsonContent(document, "/api/cart", "get", "200");
        assertJsonContent(document, "/api/cart", "get", "401");
    }

    private JsonNode openApi() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private void assertProtected(JsonNode document, String pointer, String role) {
        JsonNode operation = document.at(pointer);
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        String tagName = operation.at("/tags/0").asText();
        String description = "";
        for (JsonNode tag : document.path("tags")) {
            if (tagName.equals(tag.path("name").asText())) {
                description = tag.path("description").asText();
            }
        }
        assertThat(description).contains(role);
    }

    private void assertPageParameters(JsonNode document, String path) {
        JsonNode page = parameter(document, path, "page").path("schema");
        JsonNode size = parameter(document, path, "size").path("schema");
        assertThat(page.path("type").asText()).isEqualTo("integer");
        assertThat(size.path("type").asText()).isEqualTo("integer");
        assertThat(page.path("default").asInt()).isZero();
        assertThat(page.path("minimum").asInt()).isZero();
        assertThat(size.path("default").asInt()).isEqualTo(20);
        assertThat(size.path("minimum").asInt()).isEqualTo(1);
        assertThat(size.path("maximum").asInt()).isEqualTo(100);
    }

    private void assertParameterValues(JsonNode document, String path, String name, Set<String> expected) {
        assertThat(textValues(parameter(document, path, name).path("schema").path("enum")))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private JsonNode parameter(JsonNode document, String path, String name) {
        for (JsonNode parameter : document.path("paths").path(path).path("get").path("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        throw new AssertionError("Missing parameter " + name + " on " + path);
    }

    private void assertNullable(JsonNode schema) {
        boolean nullable = schema.path("nullable").asBoolean(false)
                || textValues(schema.path("type")).contains("null");
        assertThat(nullable).isTrue();
    }

    private void assertJsonContent(JsonNode document, String path, String method, String status) {
        assertThat(document.path("paths").path(path).path(method)
                .path("responses").path(status).path("content").has("application/json"))
                .as(method.toUpperCase() + " " + path + " " + status)
                .isTrue();
    }

    private static Map.Entry<String, Set<String>> entry(String method, String path, String... statuses) {
        return Map.entry(method + " " + path, Set.of(statuses));
    }

    private static Set<String> fieldNames(JsonNode node) {
        return iteratorToSet(node.fieldNames());
    }

    private static Set<String> textValues(JsonNode node) {
        return iteratorToSet(node.elements());
    }

    private static Set<String> iteratorToSet(Iterator<?> iterator) {
        java.util.HashSet<String> values = new java.util.HashSet<>();
        iterator.forEachRemaining(value -> values.add(
                value instanceof JsonNode json ? json.asText() : value.toString()
        ));
        return values;
    }
}
