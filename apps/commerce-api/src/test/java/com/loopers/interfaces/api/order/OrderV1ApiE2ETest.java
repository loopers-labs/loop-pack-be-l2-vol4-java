package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.enums.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String DEFAULT_USERID   = "orderUser1";
    private static final String DEFAULT_PASSWORD = "Dlaxodid1!";

    private String orderNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + suffix;
    }

    private ProductStockModel savedStock;

    @BeforeEach
    void setUp() {
        userRepository.save(new UserModel(
                new UserId(DEFAULT_USERID),
                new Password(passwordEncoder.encode(DEFAULT_PASSWORD)),
                new Name("주문유저"),
                new BirthDay("1990-01-01"),
                new Email("order@test.com"),
                UserRole.USER
        ));
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")));
        savedStock = productStockRepository.save(new ProductStockModel(product, new Price(10000L), 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", DEFAULT_USERID);
        headers.set("X-Loopers-LoginPw", DEFAULT_PASSWORD);
        return headers;
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrder(int quantity) {
        OrderV1Dto.OrderRequest request = new OrderV1Dto.OrderRequest(
                orderNumber(),
                List.of(new OrderV1Dto.OrderItemRequest(savedStock.getId(), quantity)),
                null
        );
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType);
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 요청이면, 201 CREATED와 주문 정보를 반환한다.")
        @Test
        void returnsOrder_whenRequestIsValid() {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder(2);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.REQUESTED.getDescription());
            assertThat(response.getBody().data().items()).hasSize(1);
            assertThat(response.getBody().data().totalAmount()).isEqualTo(20000L);
        }

    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("유효한 요청이면, 200 OK와 주문 목록을 반환한다.")
        @Test
        void returnsOrderList_whenRequestIsValid() {
            createOrder(1);
            createOrder(2);

            ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response =
                    testRestTemplate.exchange("/api/v1/orders", HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).hasSize(2);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문이면, 200 OK와 주문 정보를 반환한다.")
        @Test
        void returnsOrder_whenUserOwnsOrder() {
            Long orderId = createOrder(1).getBody().data().id();

            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange("/api/v1/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(authHeaders()), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(orderId);
        }

    }

    @DisplayName("POST /api/v1/orders/{orderId}/cancel")
    @Nested
    class CancelOrder {

        @DisplayName("주문 요청 상태면, 200 OK와 취소된 주문 정보를 반환한다.")
        @Test
        void cancelsOrder_whenStatusIsRequested() {
            Long orderId = createOrder(2).getBody().data().id();
            int stockBefore = productStockRepository.findById(savedStock.getId()).get().getStockQuantity().getValue();

            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange("/api/v1/orders/" + orderId + "/cancel", HttpMethod.POST, new HttpEntity<>(authHeaders()), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.CANCELLED.getDescription());

            int stockAfter = productStockRepository.findById(savedStock.getId()).get().getStockQuantity().getValue();
            assertThat(stockAfter).isEqualTo(stockBefore + 2);
        }

    }
}
