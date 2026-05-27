package com.loopers.order.interfaces;

import com.loopers.product.domain.ProductModel;
import com.loopers.product.infrastructure.ProductJpaRepository;
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

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, 200 OK와 OrderResponse를 반환하며 재고가 감소한다.")
        @Test
        void returnsOrderResponse_andDecreasesStock_whenRequestIsValid() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
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
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().status()).isEqualTo("ORDERED"),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().items().get(0).quantity()).isEqualTo(2)
            );

            // 재고 감소 확인
            ProductModel updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock()).isEqualTo(98);
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

        @DisplayName("재고가 부족한 상품이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenStockIsInsufficient() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 1, null));
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("인증 헤더가 없으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenAuthHeaderIsMissing() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
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

    @DisplayName("GET /api/v1/orders/{id}")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문 ID이면, 200 OK와 OrderResponse를 반환한다.")
        @Test
        void returnsOrderResponse_whenOrderExists() {
            // arrange
            ProductModel product = productJpaRepository.save(new ProductModel("에어맥스", "나이키 운동화", 150000L, 100, null));
            OrderV1Dto.CreateRequest createRequest = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(createRequest, authHeaders()), responseType);
            Long orderId = created.getBody().data().id();

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + orderId, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(orderId),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
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

        @DisplayName("숫자가 아닌 id이면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenIdIsNotNumber() {
            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/나나", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
