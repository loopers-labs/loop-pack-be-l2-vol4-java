package com.loopers.interfaces.api;

import com.loopers.domain.order.PaymentMethod;
import com.loopers.domain.payment.PgStatus;
import com.loopers.infrastructure.payment.FakePaymentGateway;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminOrderV1ApiE2ETest {

    private static final String ADMIN_ORDERS_PATH = "/api/v1/admin/orders";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final FakePaymentGateway fakePaymentGateway;

    private Long productId;

    @Autowired
    public AdminOrderV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp,
                                  FakePaymentGateway fakePaymentGateway) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.fakePaymentGateway = fakePaymentGateway;
    }

    @BeforeEach
    void setUp() {
        fakePaymentGateway.reset();
        signUp("buyer1", "테스터일");
        signUp("buyer2", "테스터이");
        Long brandId = createBrand("나이키");
        productId = createProduct(brandId, "에어맥스", 139000L, 50);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        fakePaymentGateway.reset();
    }

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", "testPw1234");
        return headers;
    }

    private void signUp(String loginId, String name) {
        testRestTemplate.postForEntity("/api/v1/users",
                new UserV1Dto.SignUpRequest(loginId, "testPw1234", name, LocalDate.of(1992, 6, 24), "test@example.com"),
                Object.class);
    }

    private Long createBrand(String name) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands", HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateBrandRequest(name, "설명")),
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

    private void placeOrder(String loginId) {
        OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                PaymentMethod.CARD, List.of(new OrderV1Dto.OrderLineRequest(productId, 1)), null);
        testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(loginId)),
                new ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {});
    }

    private static final ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderResponse>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> getAdminOrders(String query) {
        return testRestTemplate.exchange(ADMIN_ORDERS_PATH + query, HttpMethod.GET, HttpEntity.EMPTY, LIST_TYPE);
    }

    @DisplayName("GET /api/v1/admin/orders")
    @Nested
    class GetOrders {

        @DisplayName("운영자는 모든 사용자의 주문을 조회한다 (본인 격리 예외).")
        @Test
        void returnsAllUsersOrders() {
            placeOrder("buyer1");
            placeOrder("buyer2");

            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response = getAdminOrders("");

            assertThat(response.getBody().data()).hasSize(2);
        }

        @DisplayName("상태 필터를 주면, 해당 상태의 주문만 반환된다.")
        @Test
        void filtersByStatus() {
            fakePaymentGateway.setForcedStatus(PgStatus.SUCCESS);
            placeOrder("buyer1"); // PAID
            fakePaymentGateway.setForcedStatus(PgStatus.FAILED);
            placeOrder("buyer2"); // FAILED

            List<OrderV1Dto.OrderResponse> paid = getAdminOrders("?status=PAID").getBody().data();
            List<OrderV1Dto.OrderResponse> failed = getAdminOrders("?status=FAILED").getBody().data();

            assertThat(paid).hasSize(1);
            assertThat(paid.get(0).status()).isEqualTo("PAID");
            assertThat(failed).hasSize(1);
            assertThat(failed.get(0).status()).isEqualTo("FAILED");
        }

        @DisplayName("잘못된 상태 값을 주면, 400을 반환한다.")
        @Test
        void returns400_whenInvalidStatus() {
            ResponseEntity<Object> response = testRestTemplate.exchange(
                    ADMIN_ORDERS_PATH + "?status=INVALID", HttpMethod.GET, HttpEntity.EMPTY, Object.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
