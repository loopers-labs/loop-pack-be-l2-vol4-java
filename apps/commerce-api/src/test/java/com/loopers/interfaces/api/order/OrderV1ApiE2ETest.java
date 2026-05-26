package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String ENDPOINT_SIGNUP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {
        @DisplayName("인증된 회원이 주문 가능한 상품을 주문하면, 주문을 생성하고 상품 재고를 차감한다.")
        @Test
        void createsOrderAndDeductsStock_whenProductsAreAvailable() {
            // arrange
            signup("user1234", "abc123!?");
            BrandModel brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductModel product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderProductRequest(product.getId(), 2))
            );

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    orderResponseType()
                );

            ProductModel savedProduct = productJpaRepository.findById(product.getId()).orElseThrow();

            // assert
            OrderV1Dto.OrderResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.userLoginId()).isEqualTo("user1234"),
                () -> assertThat(data.totalAmount()).isEqualTo(60_000L),
                () -> assertThat(data.orderLines()).hasSize(1),
                () -> assertThat(data.orderLines().get(0).productId()).isEqualTo(product.getId()),
                () -> assertThat(data.failures()).isEmpty(),
                () -> assertThat(savedProduct.getStock()).isEqualTo(8)
            );
        }

        @DisplayName("일부 상품의 재고가 부족하면, 가능한 상품만 주문하고 실패 상품을 응답에 포함한다.")
        @Test
        void createsOrderWithAvailableProductsAndReturnsFailures_whenSomeProductsAreOutOfStock() {
            // arrange
            signup("user1234", "abc123!?");
            BrandModel brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductModel availableProduct = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);
            ProductModel outOfStockProduct = saveProduct(brand.getId(), "셔츠", "가벼운 셔츠", 20_000L, 1);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(
                    new OrderV1Dto.OrderProductRequest(availableProduct.getId(), 2),
                    new OrderV1Dto.OrderProductRequest(outOfStockProduct.getId(), 3)
                )
            );

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    orderResponseType()
                );

            ProductModel savedAvailableProduct = productJpaRepository.findById(availableProduct.getId()).orElseThrow();
            ProductModel savedOutOfStockProduct = productJpaRepository.findById(outOfStockProduct.getId()).orElseThrow();

            // assert
            OrderV1Dto.OrderResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.orderLines()).hasSize(1),
                () -> assertThat(data.orderLines().get(0).productId()).isEqualTo(availableProduct.getId()),
                () -> assertThat(data.failures()).hasSize(1),
                () -> assertThat(data.failures().get(0).productId()).isEqualTo(outOfStockProduct.getId()),
                () -> assertThat(savedAvailableProduct.getStock()).isEqualTo(8),
                () -> assertThat(savedOutOfStockProduct.getStock()).isEqualTo(1)
            );
        }

        @DisplayName("주문 가능한 상품이 하나도 없으면, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenNoProductCanBeOrdered() {
            // arrange
            signup("user1234", "abc123!?");
            BrandModel brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductModel product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 1);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderProductRequest(product.getId(), 2))
            );

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenCredentialHeaderIsMissing() {
            // arrange
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                List.of(new OrderV1Dto.OrderProductRequest(1L, 1))
            );

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("주문 상품 목록이 비어있으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenProductsAreEmpty() {
            // arrange
            signup("user1234", "abc123!?");
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private BrandModel saveBrand(String name, String description) {
        return brandJpaRepository.save(new BrandModel(name, description));
    }

    private ProductModel saveProduct(Long brandId, String name, String description, Long price, Integer stock) {
        return productJpaRepository.save(new ProductModel(brandId, name, description, price, stock));
    }

    private void signup(String loginId, String password) {
        UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
            loginId,
            password,
            "홍길동",
            LocalDate.of(1990, 1, 15),
            loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, String.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> orderResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<Void>> voidResponseType() {
        return new ParameterizedTypeReference<>() {};
    }
}
