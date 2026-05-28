package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
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
import org.springframework.data.domain.Page;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ORDER_ENDPOINT = "/api/v1/orders";
    private static final String RAW_PASSWORD = "Password1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private StockJpaRepository stockJpaRepository;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private UserModel testUser;
    private ProductModel savedProduct;

    @BeforeEach
    void setUp() {
        testUser = userJpaRepository.save(new UserModel(
            "testuser", passwordEncoder.encode(RAW_PASSWORD),
            "테스터", LocalDate.of(1990, 1, 15), "test@example.com"
        ));
        BrandModel brand = brandJpaRepository.save(new BrandModel("Nike", "스포츠 브랜드"));
        savedProduct = productJpaRepository.save(new ProductModel(brand, "나이키 에어맥스", 150_000));
        stockJpaRepository.save(new StockModel(savedProduct, 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", testUser.getLoginId());
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrder(int quantity) {
        Map<String, Object> request = Map.of(
            "items", List.of(Map.of("productId", savedProduct.getId(), "quantity", quantity))
        );
        return testRestTemplate.exchange(
            ORDER_ENDPOINT, HttpMethod.POST,
            new HttpEntity<>(request, authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
    }

    @DisplayName("POST /api/v1/orders 요청 시,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 주문 생성 시 200과 주문 정보가 반환된다.")
        @Test
        void returnsOrder_whenValidRequestProvided() {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder(2);

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().userId()).isEqualTo(testUser.getId()),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(300_000),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("나이키 에어맥스")
            );
        }

        @DisplayName("주문 후 재고가 차감된다.")
        @Test
        void decreasesStock_afterOrderCreated() {
            createOrder(3);

            int remaining = stockJpaRepository.findByProduct_Id(savedProduct.getId())
                .orElseThrow().getQuantity();
            assertThat(remaining).isEqualTo(7);
        }

        @DisplayName("존재하지 않는 상품으로 주문 시 404가 반환된다.")
        @Test
        void returns404_whenProductDoesNotExist() {
            Map<String, Object> request = Map.of(
                "items", List.of(Map.of("productId", 999L, "quantity", 1))
            );
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("재고 초과 수량으로 주문 시 400이 반환된다.")
        @Test
        void returns400_whenStockInsufficient() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(Map.of("items", List.of(Map.of("productId", savedProduct.getId(), "quantity", 999))), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("주문 항목이 비어있으면 400이 반환된다.")
        @Test
        void returns400_whenItemsIsEmpty() {
            Map<String, Object> request = Map.of("items", List.of());
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders 요청 시,")
    @Nested
    class GetMyOrders {

        @DisplayName("본인의 주문 목록이 반환된다.")
        @Test
        void returnsMyOrders_whenOrdersExist() {
            createOrder(1);
            createOrder(2);

            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderResponse>>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @DisplayName("주문이 없으면 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoOrders() {
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderResponse>>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} 요청 시,")
    @Nested
    class GetOrder {

        @DisplayName("본인의 주문 상세 조회 시 200과 주문 정보가 반환된다.")
        @Test
        void returnsOrder_whenOrderBelongsToUser() {
            Long orderId = createOrder(1).getBody().data().id();

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(orderId),
                () -> assertThat(response.getBody().data().userId()).isEqualTo(testUser.getId())
            );
        }

        @DisplayName("존재하지 않는 주문 조회 시 404가 반환된다.")
        @Test
        void returns404_whenOrderDoesNotExist() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT + "/999", HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("타인의 주문 조회 시 404가 반환된다.")
        @Test
        void returns404_whenOrderBelongsToOtherUser() {
            UserModel otherUser = userJpaRepository.save(new UserModel(
                "other", passwordEncoder.encode(RAW_PASSWORD),
                "타인", LocalDate.of(1995, 3, 20), "other@example.com"
            ));
            Long orderId = createOrder(1).getBody().data().id();

            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set("X-Loopers-LoginId", otherUser.getLoginId());
            otherHeaders.set("X-Loopers-LoginPw", RAW_PASSWORD);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDER_ENDPOINT + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(otherHeaders),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    record PageResponse<T>(List<T> content, long totalElements, int totalPages, int size, int number) {}
}
