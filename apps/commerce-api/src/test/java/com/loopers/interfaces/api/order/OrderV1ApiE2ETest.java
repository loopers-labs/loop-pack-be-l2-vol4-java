package com.loopers.interfaces.api.order;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.order.OrderSnapshot;
import com.loopers.domain.order.OrderSnapshotItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.application.user.UserApplicationService;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.order.OrderMapper;
import com.loopers.domain.order.OrderEntity;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private static final String DEFAULT_LOGIN_ID = "testuser1";
    private static final String DEFAULT_PASSWORD = "Test1234!";

    private final TestRestTemplate testRestTemplate;
    private final BrandApplicationService brandApplicationService;
    private final ProductApplicationService productApplicationService;
    private final OrderJpaRepository orderJpaRepository;
    private final UserApplicationService userApplicationService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandApplicationService brandApplicationService,
            ProductApplicationService productApplicationService,
            OrderJpaRepository orderJpaRepository,
            UserApplicationService userApplicationService,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandApplicationService = brandApplicationService;
        this.productApplicationService = productApplicationService;
        this.orderJpaRepository = orderJpaRepository;
        this.userApplicationService = userApplicationService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createUser() {
        return userApplicationService.signup(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD, "홍길동",
                LocalDate.of(1995, 1, 1), "test@test.com").id();
    }

    private ProductInfo createProduct(int quantity) {
        var brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
        return productApplicationService.createProduct(brand.id(), "에어맥스", "운동화", 100_000L, quantity);
    }

    private Long createOrderDirectly(Long userId, ProductInfo product, int quantity) {
        long subtotal = product.price() * quantity;
        OrderSnapshot snapshot = new OrderSnapshot(
                List.of(new OrderSnapshotItem(product.id(), product.name(), product.price(), quantity, subtotal)),
                subtotal, 0L, subtotal, null
        );
        OrderEntity order = new OrderEntity(userId, snapshot);
        return orderJpaRepository.save(OrderMapper.toJpaEntity(order)).getId();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, DEFAULT_LOGIN_ID);
        headers.set(HEADER_LOGIN_PW, DEFAULT_PASSWORD);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LDAP, ADMIN_LDAP_VALUE);
        return headers;
    }

    // ─────────────────────────────────────────────
    // POST /api/v1/orders
    // ─────────────────────────────────────────────

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("인증 헤더 없이 요청하면 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(1L, 1)), null
            );

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST, new HttpEntity<>(request), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("유효한 요청이면 201 Created와 주문 ID를 반환한다.")
        @Test
        void returnsCreated_whenRequestIsValid() {
            createUser();
            ProductInfo product = createProduct(10);

            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(product.id(), 2)), null
            );

            ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreateOrderResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.CreateOrderResponse>> response = testRestTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST, new HttpEntity<>(request, userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().orderId()).isNotNull();
        }

        @DisplayName("items가 빈 배열이면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenItemsIsEmpty() {
            createUser();

            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(), null);

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST, new HttpEntity<>(request, userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 상품 ID를 포함하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            createUser();

            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(999L, 1)), null
            );

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST, new HttpEntity<>(request, userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면 400을 반환한다.")
        @Test
        void returnsBadRequest_whenInsufficientStock() {
            createUser();
            ProductInfo product = createProduct(1);

            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                    List.of(new OrderV1Dto.CreateOrderRequest.OrderItemRequest(product.id(), 5)), null
            );

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST, new HttpEntity<>(request, userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api/v1/orders
    // ─────────────────────────────────────────────

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("내 주문 목록을 조회하면 200과 주문 목록을 반환한다.")
        @Test
        void returnsOrders_whenRequestIsValid() {
            Long userId = createUser();
            ProductInfo product = createProduct(10);
            createOrderDirectly(userId, product, 1);

            ParameterizedTypeReference<ApiResponse<PageResult<OrderV1Dto.OrderResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<OrderV1Dto.OrderResponse>>> response =
                    testRestTemplate.exchange(
                            "/api/v1/orders?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(userHeaders()), type
                    );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
            OrderV1Dto.OrderResponse item = response.getBody().data().content().get(0);
            assertThat(item.orderId()).isNotNull();
            assertThat(item.finalAmount()).isEqualTo(100_000L);
        }

        @DisplayName("startAt/endAt 날짜 필터로 내 주문 목록을 조회할 수 있다.")
        @Test
        void returnsFilteredOrders_whenDateFilterApplied() {
            Long userId = createUser();
            ProductInfo product = createProduct(10);
            createOrderDirectly(userId, product, 1);

            ParameterizedTypeReference<ApiResponse<PageResult<OrderV1Dto.OrderResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<OrderV1Dto.OrderResponse>>> response =
                    testRestTemplate.exchange(
                            "/api/v1/orders?startAt=2020-01-01&endAt=2099-12-31&page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(userHeaders()), type
                    );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api/v1/orders/{orderId}
    // ─────────────────────────────────────────────

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("내 주문을 단건 조회하면 200과 주문 상세를 반환한다.")
        @Test
        void returnsOrder_whenRequestIsValid() {
            Long userId = createUser();
            ProductInfo product = createProduct(10);
            Long orderId = createOrderDirectly(userId, product, 2);

            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange(
                            "/api/v1/orders/" + orderId,
                            HttpMethod.GET, new HttpEntity<>(userHeaders()), type
                    );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().orderId()).isEqualTo(orderId);
            assertThat(response.getBody().data().finalAmount()).isEqualTo(200_000L);
            assertThat(response.getBody().data().items()).hasSize(1);
            assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("에어맥스");
        }

        @DisplayName("타인의 주문을 조회하면 404를 반환한다.")
        @Test
        void returnsNotFound_whenAccessingOtherUserOrder() {
            createUser();
            Long otherUserId = userApplicationService.signup("otheruser1", "Other1234!", "김철수",
                    LocalDate.of(1990, 5, 15), "other@test.com").id();
            ProductInfo product = createProduct(10);
            Long otherOrderId = createOrderDirectly(otherUserId, product, 1);

            ParameterizedTypeReference<ApiResponse<Void>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    "/api/v1/orders/" + otherOrderId,
                    HttpMethod.GET, new HttpEntity<>(userHeaders()), type
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api-admin/v1/orders
    // ─────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    class AdminGetOrders {

        @DisplayName("어드민이 전체 주문 목록을 조회하면 200과 userId가 포함된 주문 목록을 반환한다.")
        @Test
        void returnsAllOrders_whenAdminRequestIsValid() {
            Long userId = createUser();
            ProductInfo product = createProduct(10);
            createOrderDirectly(userId, product, 1);

            ParameterizedTypeReference<ApiResponse<PageResult<OrderV1Dto.AdminOrderResponse>>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResult<OrderV1Dto.AdminOrderResponse>>> response =
                    testRestTemplate.exchange(
                            "/api-admin/v1/orders?page=0&size=20",
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().totalElements()).isEqualTo(1);
            OrderV1Dto.AdminOrderResponse item = response.getBody().data().content().get(0);
            assertThat(item.userId()).isEqualTo(userId);
        }
    }

    // ─────────────────────────────────────────────
    // GET /api-admin/v1/orders/{orderId}
    // ─────────────────────────────────────────────

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    class AdminGetOrder {

        @DisplayName("어드민이 주문 단건 조회하면 200과 userId가 포함된 주문 상세를 반환한다.")
        @Test
        void returnsOrder_whenAdminRequestIsValid() {
            Long userId = createUser();
            ProductInfo product = createProduct(10);
            Long orderId = createOrderDirectly(userId, product, 1);

            ParameterizedTypeReference<ApiResponse<OrderV1Dto.AdminOrderResponse>> type =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.AdminOrderResponse>> response =
                    testRestTemplate.exchange(
                            "/api-admin/v1/orders/" + orderId,
                            HttpMethod.GET, new HttpEntity<>(adminHeaders()), type
                    );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().orderId()).isEqualTo(orderId);
            assertThat(response.getBody().data().userId()).isEqualTo(userId);
        }
    }
}
