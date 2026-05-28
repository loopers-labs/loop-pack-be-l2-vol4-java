package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.money.Money;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users";
    private static final String ENDPOINT_ORDER = "/api/v1/orders";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        OrderJpaRepository orderJpaRepository,
        OrderItemJpaRepository orderItemJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.orderItemJpaRepository = orderItemJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void signup(String loginId, String password) {
        UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
            loginId, password, "김민우", LocalDate.of(1990, 1, 1), loginId + "@example.com"
        );
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
            new HttpEntity<>(request), responseType);
    }

    private Product saveProduct(int stock) {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(
            new Product("에어맥스", "편한 러닝화",
                new Money(BigDecimal.valueOf(1000)), new Stock(stock), brand.getId())
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class Place {
        @DisplayName("유효한 인증 헤더와 주문 항목으로 요청하면, 주문이 생성되고 재고가 차감된다.")
        @Test
        void createsOrderAndDecreasesStock_whenRequestIsValid() {
            // arrange
            signup("minwoo01", "Passw0rd!");
            Product product = saveProduct(10);
            HttpHeaders headers = authHeaders("minwoo01", "Passw0rd!");
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                List.of(new OrderV1Dto.OrderLineRequest(product.getId(), 3))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, headers), responseType);

            // assert
            Product reloaded = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.COMPLETED),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000)),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(orderJpaRepository.findAll()).hasSize(1),
                () -> assertThat(orderItemJpaRepository.findAll()).hasSize(1),
                () -> assertThat(reloaded.getStock().getQuantity()).isEqualTo(7)
            );
        }

        @DisplayName("재고보다 많은 수량을 주문하면, 400 BAD_REQUEST 응답을 받고 주문이 저장되지 않는다.")
        @Test
        void throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            signup("minwoo01", "Passw0rd!");
            Product product = saveProduct(5);
            HttpHeaders headers = authHeaders("minwoo01", "Passw0rd!");
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                List.of(new OrderV1Dto.OrderLineRequest(product.getId(), 10))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty(),
                () -> assertThat(orderItemJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 상품 ID로 주문하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            signup("minwoo01", "Passw0rd!");
            HttpHeaders headers = authHeaders("minwoo01", "Passw0rd!");
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                List.of(new OrderV1Dto.OrderLineRequest(999L, 1))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenHeadersAreMissing() {
            // arrange
            Product product = saveProduct(10);
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(
                List.of(new OrderV1Dto.OrderLineRequest(product.getId(), 1))
            );

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
