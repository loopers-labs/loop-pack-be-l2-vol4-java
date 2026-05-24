package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStockService;
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

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Loopers!2026";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        ProductService productService,
        ProductStockService productStockService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.productService = productService;
        this.productStockService = productStockService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("인증 사용자와 주문 가능한 상품들이 주어지면 201 CREATED와 주문 스냅샷을 반환하고 재고를 차감한다.")
        @Test
        void returnsCreatedOrderAndDeductsStock_whenAuthenticatedUserAndOrderableProductsAreProvided() {
            // arrange
            signUpUser();
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            Product iphoneMax = createProduct(brand, "아이폰 16 Pro Max", "더 큰 화면과 향상된 배터리를 제공하는 스마트폰", 1_900_000L, 5);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                new OrderV1Dto.CreateOrderRequest.Item(iphone.getId(), 2),
                new OrderV1Dto.CreateOrderRequest.Item(iphoneMax.getId(), 1)
            ));

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder(request, authHeaders());

            // assert
            OrderV1Dto.OrderResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(data.id()).isNotNull(),
                () -> assertThat(data.orderTotalPrice()).isEqualTo(5_000_000L),
                () -> assertThat(data.items())
                    .extracting(OrderV1Dto.OrderResponse.Item::productName)
                    .containsExactly("아이폰 16 Pro", "아이폰 16 Pro Max"),
                () -> assertThat(data.items())
                    .extracting(OrderV1Dto.OrderResponse.Item::brandName)
                    .containsExactly("애플", "애플"),
                () -> assertThat(data.items())
                    .extracting(OrderV1Dto.OrderResponse.Item::totalPrice)
                    .containsExactly(3_100_000L, 1_900_000L),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(8),
                () -> assertThat(productStockService.getProductStock(iphoneMax.getId()).getQuantity()).isEqualTo(4)
            );
        }

        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthenticationHeadersAreMissing() {
            // arrange
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                new OrderV1Dto.CreateOrderRequest.Item(iphone.getId(), 1)
            ));

            // act
            ResponseEntity<ApiResponse<Object>> response = createOrderForError(request, new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("주문 수량보다 재고가 부족하면 409 CONFLICT를 반환하고 재고를 차감하지 않는다.")
        @Test
        void returnsConflictAndKeepsStock_whenStockIsInsufficient() {
            // arrange
            signUpUser();
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                new OrderV1Dto.CreateOrderRequest.Item(iphone.getId(), 11)
            ));

            // act
            ResponseEntity<ApiResponse<Object>> response = createOrderForError(request, authHeaders());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10)
            );
        }

        @DisplayName("같은 상품 ID가 중복되면 400 BAD_REQUEST를 반환하고 재고를 차감하지 않는다.")
        @Test
        void returnsBadRequestAndKeepsStock_whenProductIdIsDuplicated() {
            // arrange
            signUpUser();
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                new OrderV1Dto.CreateOrderRequest.Item(iphone.getId(), 1),
                new OrderV1Dto.CreateOrderRequest.Item(iphone.getId(), 2)
            ));

            // act
            ResponseEntity<ApiResponse<Object>> response = createOrderForError(request, authHeaders());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(10)
            );
        }
    }

    private Brand createBrand() {
        return brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
    }

    private Product createProduct(Brand brand, String name, String description, long price, int stockQuantity) {
        Product product = productService.createProduct(brand.getId(), name, description, price);
        productStockService.createProductStock(product.getId(), stockQuantity);
        return product;
    }

    private void signUpUser() {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            LOGIN_ID,
            PASSWORD,
            "김성호",
            LocalDate.of(1993, 11, 3),
            "loopers@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_USERS, request, ApiResponse.class);
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrder(
        OrderV1Dto.CreateOrderRequest request,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ORDERS,
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<Object>> createOrderForError(
        OrderV1Dto.CreateOrderRequest request,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ORDERS,
            HttpMethod.POST,
            new HttpEntity<>(request, headers),
            responseType
        );
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, PASSWORD);
        return headers;
    }
}
