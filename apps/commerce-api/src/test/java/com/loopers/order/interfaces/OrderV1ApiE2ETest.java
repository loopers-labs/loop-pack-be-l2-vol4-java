package com.loopers.order.interfaces;

import com.loopers.order.domain.OrderStatus;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.infrastructure.StockJpaRepository;
import com.loopers.support.response.ApiResponse;
import com.loopers.user.domain.Gender;
import com.loopers.user.interfaces.UserV1Dto;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String LOGIN_ID = "user1";
    private static final String PASSWORD = "Pass123!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.SignUpRequest(LOGIN_ID, PASSWORD, "홍길동", "test@example.com", "2000-01-01", Gender.MALE)),
            Void.class
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    private ProductModel savedProduct(int totalStock) {
        ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, null));
        stockJpaRepository.save(new StockModel(product.getId(), totalStock));
        return product;
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, 200 OK와 PENDING_PAYMENT 상태의 OrderResponse를 반환하며 재고 변동이 없다.")
        @Test
        void returnsOrderResponse_withPendingPaymentStatus_andNoStockChange_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.PENDING_PAYMENT.name()),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
            );

            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertThat(stock.availableStock()).isEqualTo(100);
        }

        @DisplayName("존재하지 않는 productId가 포함되면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            // arrange
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(999L, 1))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 헤더가 없으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenAuthHeaderIsMissing() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api/v1/orders/{id}/pay/start")
    @Nested
    class StartPayment {

        @DisplayName("정상 요청이면, 200 OK와 재고가 선점된다.")
        @Test
        void reservesStock_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderV1Dto.CreateRequest createRequest = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 5))
            );
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            Long orderId = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest, authHeaders()), responseType
            ).getBody().data().id();

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/pay/start", HttpMethod.POST, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertThat(stock.getReservedStock()).isEqualTo(5);
        }

        // [fix] 재고 검증이 createOrder로 이동됨에 따라 POST /orders 단계에서 400 검증으로 변경
        @DisplayName("재고가 부족하면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenStockIsInsufficient() {
            // arrange
            ProductModel product = savedProduct(1);
            OrderV1Dto.CreateRequest createRequest = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 5))
            );
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest, authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api/v1/orders/{id}/pay/confirm")
    @Nested
    class ConfirmPayment {

        @DisplayName("정상 요청이면, 200 OK와 CONFIRMED 상태, 재고가 차감된다.")
        @Test
        void confirmsPayment_andDeductsStock_whenRequestIsValid() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderV1Dto.CreateRequest createRequest = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 3))
            );
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            Long orderId = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest, authHeaders()), responseType
            ).getBody().data().id();
            testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/pay/start", HttpMethod.POST, new HttpEntity<>(authHeaders()), responseType);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/pay/confirm", HttpMethod.POST, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.CONFIRMED.name())
            );
            StockModel stock = stockJpaRepository.findByProductId(product.getId()).orElseThrow();
            assertThat(stock.getTotalStock()).isEqualTo(97);
        }

        @DisplayName("존재하지 않는 orderId이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOrderNotExists() {
            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/999/pay/confirm", HttpMethod.POST, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문이면, 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderV1Dto.CreateRequest createRequest = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            Long orderId = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest, authHeaders()), responseType
            ).getBody().data().id();
            testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/pay/start", HttpMethod.POST, new HttpEntity<>(authHeaders()), responseType);

            testRestTemplate.exchange(
                "/api/v1/users", HttpMethod.POST,
                new HttpEntity<>(new UserV1Dto.SignUpRequest("user2", PASSWORD, "김철수", "user2@example.com", "1995-05-05", Gender.MALE)),
                Void.class
            );
            HttpHeaders user2Headers = new HttpHeaders();
            user2Headers.set("X-Loopers-LoginId", "user2");
            user2Headers.set("X-Loopers-LoginPw", PASSWORD);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + orderId + "/pay/confirm", HttpMethod.POST, new HttpEntity<>(user2Headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("GET /api/v1/orders?startAt=...&endAt=...")
    @Nested
    class GetOrders {

        @DisplayName("날짜 범위 내에 주문이 있으면, 200 OK와 주문 목록을 반환한다.")
        @Test
        void returnsOrderList_whenOrdersExistInRange() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderV1Dto.CreateRequest createRequest = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {});

            LocalDate today = LocalDate.now();
            String url = ENDPOINT + "?startAt=" + today + "&endAt=" + today;

            // act
            ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).hasSize(1)
            );
        }

        @DisplayName("날짜 범위 내에 주문이 없으면, 200 OK와 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoOrdersInRange() {
            // arrange
            LocalDate past = LocalDate.of(2000, 1, 1);
            String url = ENDPOINT + "?startAt=" + past + "&endAt=" + past;

            // act
            ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }
    }

    @DisplayName("GET /api/v1/orders/{id}")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문 ID이면, 200 OK와 OrderResponse를 반환한다.")
        @Test
        void returnsOrderResponse_whenOrderExists() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderV1Dto.CreateRequest createRequest = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            Long orderId = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest, authHeaders()), responseType
            ).getBody().data().id();

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + orderId, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(orderId)
            );
        }

        @DisplayName("존재하지 않는 주문 ID이면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOrderNotExists() {
            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문 ID이면, 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            ProductModel product = savedProduct(100);
            OrderV1Dto.CreateRequest createRequest = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            Long orderId = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest, authHeaders()), responseType
            ).getBody().data().id();

            testRestTemplate.exchange(
                "/api/v1/users", HttpMethod.POST,
                new HttpEntity<>(new UserV1Dto.SignUpRequest("user2", PASSWORD, "김철수", "user2@example.com", "1995-05-05", Gender.MALE)),
                Void.class
            );
            HttpHeaders user2Headers = new HttpHeaders();
            user2Headers.set("X-Loopers-LoginId", "user2");
            user2Headers.set("X-Loopers-LoginPw", PASSWORD);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + orderId, HttpMethod.GET, new HttpEntity<>(user2Headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
