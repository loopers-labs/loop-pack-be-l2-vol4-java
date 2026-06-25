package com.loopers.interfaces.api;

import com.loopers.domain.order.PaymentMethod;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ORDERS_PATH = "/api/v1/orders";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    public OrderV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        signUp("buyer", "testPw1234", "구매자");
        signUp("other", "testPw1234", "타인");
        Long brandId = createBrand("나이키", "스포츠");
        productId = createProduct(brandId, "에어맥스", 139000L, 10);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", loginPw);
        return headers;
    }

    private void signUp(String loginId, String password, String name) {
        testRestTemplate.postForEntity("/api/v1/users",
                new UserV1Dto.SignUpRequest(loginId, password, name, LocalDate.of(1992, 6, 24), "test@example.com"),
                Object.class);
    }

    private Long createBrand(String name, String description) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands", HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateBrandRequest(name, description)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private Long createProduct(Long brandId, String name, Long price, Integer stock) {
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products", HttpMethod.POST,
                new HttpEntity<>(new ProductV1Dto.CreateProductRequest(brandId, name, "설명", null, price, stock)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private static final ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> ORDER_TYPE =
            new ParameterizedTypeReference<>() {};

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> placeOrder(String loginId, int quantity) {
        OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                PaymentMethod.CARD, List.of(new OrderV1Dto.OrderLineRequest(productId, quantity)), null);
        return testRestTemplate.exchange(
                ORDERS_PATH, HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(loginId, "testPw1234")), ORDER_TYPE);
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class PlaceOrder {

        @DisplayName("주문하면, 200과 PENDING 상태·합계 금액을 반환한다. (결제는 분리 — 03 §3.7)")
        @Test
        void returnsPendingOrder_whenPlaced() {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = placeOrder("buyer", 2);

            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PENDING"),
                    () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(278000L),
                    () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("재고보다 많이 주문하면, 409를 반환한다.")
        @Test
        void returns409_whenStockInsufficient() {
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                    PaymentMethod.CARD, List.of(new OrderV1Dto.OrderLineRequest(productId, 999)), null);

            ResponseEntity<Object> response = testRestTemplate.exchange(
                    ORDERS_PATH, HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("buyer", "testPw1234")), Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문을 조회하면, 200과 주문을 반환한다.")
        @Test
        void returnsOrder_whenOwner() {
            Long orderId = placeOrder("buyer", 1).getBody().data().id();

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ORDERS_PATH + "/" + orderId, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("buyer", "testPw1234")), ORDER_TYPE);

            assertAll(
                    () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(orderId)
            );
        }

        @DisplayName("타인의 주문을 조회하면, 404를 반환한다. (01 §7.4 정보 노출 방지)")
        @Test
        void returns404_whenNotOwner() {
            Long orderId = placeOrder("buyer", 1).getBody().data().id();

            ResponseEntity<Object> response = testRestTemplate.exchange(
                    ORDERS_PATH + "/" + orderId, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("other", "testPw1234")), Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders (내 주문 목록)")
    @Nested
    class GetMyOrders {

        private static final ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderResponse>>> LIST_TYPE =
                new ParameterizedTypeReference<>() {};

        @DisplayName("본인 주문 목록을 조회하면, 본인 주문만 반환된다.")
        @Test
        void returnsMyOrders() {
            placeOrder("buyer", 1);
            placeOrder("buyer", 2);

            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response = testRestTemplate.exchange(
                    ORDERS_PATH, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("buyer", "testPw1234")), LIST_TYPE);

            assertThat(response.getBody().data()).hasSize(2);
        }

        @DisplayName("주문이 없는 사용자는 빈 목록을 받는다.")
        @Test
        void returnsEmpty_whenNoOrders() {
            placeOrder("buyer", 1);

            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response = testRestTemplate.exchange(
                    ORDERS_PATH, HttpMethod.GET,
                    new HttpEntity<>(authHeaders("other", "testPw1234")), LIST_TYPE);

            assertThat(response.getBody().data()).isEmpty();
        }
    }
}
