package com.loopers.interfaces.api;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockEntity;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ORDERS_URL = "/api/v1/orders";
    private static final String USERS_URL = "/api/v1/users";

    private static final String LOGIN_ID = "orderuser";
    private static final String LOGIN_PW = "pAssWord1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private ProductStockJpaRepository productStockJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.UserJoinRequest(LOGIN_ID, LOGIN_PW, "루퍼스", LocalDate.of(2000, 1, 1), "order@test.com")),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );

        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
        productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", LOGIN_PW);
        return headers;
    }

    private Long createOrder(int quantity) {
        OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
            List.of(new OrderV1Dto.OrderCreateRequest.Item(productId, quantity)), null
        );
        ResponseEntity<ApiResponse<OrderV1Dto.OrderCreateResponse>> response = testRestTemplate.exchange(
            ORDERS_URL, HttpMethod.POST,
            new HttpEntity<>(request, authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().orderId();
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 요청을 주면, 주문이 생성되고 201과 orderId를 반환한다.")
        @Test
        void returnsCreatedWithOrderId_whenValidRequestProvided() {
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                List.of(new OrderV1Dto.OrderCreateRequest.Item(productId, 2)), null
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderCreateResponse>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().orderId()).isNotNull()
            );
        }

        @DisplayName("인증 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                List.of(new OrderV1Dto.OrderCreateRequest.Item(productId, 1)), null
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderCreateResponse>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 상품을 주문하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                List.of(new OrderV1Dto.OrderCreateRequest.Item(9999L, 1)), null
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderCreateResponse>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면, 409를 반환한다.")
        @Test
        void returnsConflict_whenStockIsInsufficient() {
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                List.of(new OrderV1Dto.OrderCreateRequest.Item(productId, 999)), null
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderCreateResponse>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("유효한 기간으로 조회하면, 본인의 주문 목록을 반환한다.")
        @Test
        void returnsOrderList_whenValidPeriodProvided() {
            createOrder(2);
            String today = LocalDate.now().toString();

            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderSummary>>> response = testRestTemplate.exchange(
                ORDERS_URL + "?startAt=" + today + "&endAt=" + today,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1)
            );
        }

        @DisplayName("해당 기간에 주문이 없으면, 빈 배열을 반환한다.")
        @Test
        void returnsEmptyList_whenNoOrdersInPeriod() {
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderSummary>>> response = testRestTemplate.exchange(
                ORDERS_URL + "?startAt=2000-01-01&endAt=2000-01-31",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("endAt이 startAt보다 이전이면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenEndAtIsBeforeStartAt() {
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderSummary>>> response = testRestTemplate.exchange(
                ORDERS_URL + "?startAt=2026-01-31&endAt=2026-01-01",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("startAt이 누락되면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenStartAtIsMissing() {
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderSummary>>> response = testRestTemplate.exchange(
                ORDERS_URL + "?endAt=2026-01-31",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            String today = LocalDate.now().toString();

            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderSummary>>> response = testRestTemplate.exchange(
                ORDERS_URL + "?startAt=" + today + "&endAt=" + today,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("본인의 주문을 조회하면, 주문 상세 정보와 주문 상품 정보를 반환한다.")
        @Test
        void returnsOrderDetail_whenOrderBelongsToUser() {
            Long orderId = createOrder(3);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productId()).isEqualTo(productId)
            );
        }

        @DisplayName("존재하지 않는 주문을 조회하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/9999",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("타인의 주문을 조회하면, 403을 반환한다.")
        @Test
        void returnsForbidden_whenOrderBelongsToOtherUser() {
            Long orderId = createOrder(1);

            String otherId = "otheruser";
            testRestTemplate.exchange(
                USERS_URL, HttpMethod.POST,
                new HttpEntity<>(new UserV1Dto.UserJoinRequest(otherId, LOGIN_PW, "타인", LocalDate.of(2000, 1, 1), "other@test.com")),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            );

            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set("X-Loopers-LoginId", otherId);
            otherHeaders.set("X-Loopers-LoginPw", LOGIN_PW);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(otherHeaders),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("인증 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            Long orderId = createOrder(1);

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
