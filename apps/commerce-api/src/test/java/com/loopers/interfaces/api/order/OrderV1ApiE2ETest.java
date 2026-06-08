package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.coupon.policy.FixedCouponDiscountPolicy;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStockService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
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
import java.time.ZonedDateTime;
import java.util.Collections;
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
    private static final String OTHER_LOGIN_ID = "jungwon01";
    private static final String OTHER_PASSWORD = "Jungwon!2026";
    private static final String OTHER_NAME = "정원이";
    private static final LocalDate OTHER_BIRTH_DATE = LocalDate.of(2004, 5, 25);
    private static final String OTHER_EMAIL = "jungwon@example.com";
    private static final String COUPON_NAME = "1주년 정액 할인 쿠폰";
    private static final ZonedDateTime EXPIRED_AT = ZonedDateTime.parse("2026-12-31T23:59:59+09:00");
    private static final FixedCouponDiscountPolicy FIXED_POLICY = new FixedCouponDiscountPolicy();

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        ProductService productService,
        ProductStockService productStockService,
        CouponTemplateRepository couponTemplateRepository,
        UserCouponRepository userCouponRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.productService = productService;
        this.productStockService = productStockService;
        this.couponTemplateRepository = couponTemplateRepository;
        this.userCouponRepository = userCouponRepository;
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

        @DisplayName("인증 사용자와 사용 가능한 쿠폰이 주어지면 201 CREATED와 할인 금액이 반영된 주문 스냅샷을 반환한다.")
        @Test
        void returnsCreatedOrderWithDiscount_whenAuthenticatedUserAndCouponAreProvided() {
            // arrange
            Long userId = signUpUser();
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            CouponTemplate couponTemplate = createFixedCouponTemplate();
            UserCoupon userCoupon = userCouponRepository.save(couponTemplate.issue(userId, EXPIRED_AT.minusDays(1)));
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(List.of(
                new OrderV1Dto.CreateOrderRequest.Item(iphone.getId(), 1)
            ), userCoupon.getId());

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder(request, authHeaders());

            // assert
            OrderV1Dto.OrderResponse data = response.getBody().data();
            UserCoupon usedCoupon = userCouponRepository.findIssuedCoupon(userId, couponTemplate.getId())
                .orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(data.id()).isNotNull(),
                () -> assertThat(data.appliedUserCouponId()).isEqualTo(userCoupon.getId()),
                () -> assertThat(data.orderTotalPrice()).isEqualTo(1_550_000L),
                () -> assertThat(data.discountAmount()).isEqualTo(2_000L),
                () -> assertThat(data.paymentAmount()).isEqualTo(1_548_000L),
                () -> assertThat(usedCoupon.getStatus()).isEqualTo(UserCouponStatus.USED),
                () -> assertThat(productStockService.getProductStock(iphone.getId()).getQuantity()).isEqualTo(9)
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

        @DisplayName("주문 항목 목록에 빈 항목이 있으면 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenOrderItemIsNull() {
            // arrange
            signUpUser();
            OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
                Collections.singletonList(null)
            );

            // act
            ResponseEntity<ApiResponse<Object>> response = createOrderForError(request, authHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("인증 사용자의 주문이 있으면, 기간 조건에 맞는 내 주문 목록을 최신순 Page로 반환한다.")
        @Test
        void returnsMyOrdersByLatest_whenAuthenticatedUserAndDateRangeAreProvided() {
            // arrange
            signUpUser();
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            Product iphoneMax = createProduct(brand, "아이폰 16 Pro Max", "더 큰 화면과 향상된 배터리를 제공하는 스마트폰", 1_900_000L, 5);
            OrderV1Dto.OrderResponse firstOrder = createOrder(new OrderV1Dto.CreateOrderRequest(List.of(
                new OrderV1Dto.CreateOrderRequest.Item(iphone.getId(), 1)
            )), authHeaders()).getBody().data();
            OrderV1Dto.OrderResponse secondOrder = createOrder(new OrderV1Dto.CreateOrderRequest(List.of(
                new OrderV1Dto.CreateOrderRequest.Item(iphoneMax.getId(), 1)
            )), authHeaders()).getBody().data();
            LocalDate today = LocalDate.now();

            // act
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderResponse>>> response = getOrders(
                "?startAt=" + today + "&endAt=" + today,
                authHeaders()
            );

            // assert
            PageResponse<OrderV1Dto.OrderResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalElements()).isEqualTo(2),
                () -> assertThat(data.content())
                    .extracting(OrderV1Dto.OrderResponse::id)
                    .containsExactly(secondOrder.id(), firstOrder.id()),
                () -> assertThat(data.content().get(0).items())
                    .extracting(OrderV1Dto.OrderResponse.Item::productName)
                    .containsExactly("아이폰 16 Pro Max"),
                () -> assertThat(data.content().get(1).items())
                    .extracting(OrderV1Dto.OrderResponse.Item::productName)
                    .containsExactly("아이폰 16 Pro")
            );
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문 ID가 주어지면, 주문 스냅샷 상세를 반환한다.")
        @Test
        void returnsMyOrderDetail_whenOrderBelongsToAuthenticatedUser() {
            // arrange
            signUpUser();
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            OrderV1Dto.OrderResponse createdOrder = createOrder(new OrderV1Dto.CreateOrderRequest(List.of(
                new OrderV1Dto.CreateOrderRequest.Item(iphone.getId(), 2)
            )), authHeaders()).getBody().data();

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = getOrder(createdOrder.id(), authHeaders());

            // assert
            OrderV1Dto.OrderResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(createdOrder.id()),
                () -> assertThat(data.orderTotalPrice()).isEqualTo(3_100_000L),
                () -> assertThat(data.items())
                    .extracting(OrderV1Dto.OrderResponse.Item::brandName)
                    .containsExactly("애플"),
                () -> assertThat(data.items())
                    .extracting(OrderV1Dto.OrderResponse.Item::productName)
                    .containsExactly("아이폰 16 Pro"),
                () -> assertThat(data.items())
                    .extracting(OrderV1Dto.OrderResponse.Item::unitPrice)
                    .containsExactly(1_550_000L)
            );
        }

        @DisplayName("존재하지 않는 주문 ID가 주어지면 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // arrange
            signUpUser();

            // act
            ResponseEntity<ApiResponse<Object>> response = getOrderForError(999_999L, authHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("다른 사용자의 주문 ID가 주어지면 403 FORBIDDEN을 반환한다.")
        @Test
        void returnsForbidden_whenOrderBelongsToAnotherUser() {
            // arrange
            signUpUser();
            signUpUser(OTHER_LOGIN_ID, OTHER_PASSWORD, OTHER_NAME, OTHER_BIRTH_DATE, OTHER_EMAIL);
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            OrderV1Dto.OrderResponse createdOrder = createOrder(new OrderV1Dto.CreateOrderRequest(List.of(
                new OrderV1Dto.CreateOrderRequest.Item(iphone.getId(), 1)
            )), authHeaders()).getBody().data();

            // act
            ResponseEntity<ApiResponse<Object>> response = getOrderForError(
                createdOrder.id(),
                authHeaders(OTHER_LOGIN_ID, OTHER_PASSWORD)
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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

    private CouponTemplate createFixedCouponTemplate() {
        return couponTemplateRepository.save(CouponTemplate.create(
            COUPON_NAME,
            CouponType.FIXED,
            2_000L,
            10_000L,
            EXPIRED_AT,
            FIXED_POLICY
        ));
    }

    private Long signUpUser() {
        return signUpUser(
            LOGIN_ID,
            PASSWORD,
            "김성호",
            LocalDate.of(1993, 11, 3),
            "loopers@example.com"
        );
    }

    private Long signUpUser(String loginId, String password, String name, LocalDate birthDate, String email) {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            loginId,
            password,
            name,
            birthDate,
            email
        );
        ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
            ENDPOINT_USERS,
            HttpMethod.POST,
            new HttpEntity<>(request),
            responseType
        );
        return response.getBody().data().id();
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

    private ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderResponse>>> getOrders(
        String query,
        HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<PageResponse<OrderV1Dto.OrderResponse>>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ORDERS + query,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> getOrder(Long orderId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ORDERS + "/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<Object>> getOrderForError(Long orderId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ORDERS + "/" + orderId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private HttpHeaders authHeaders() {
        return authHeaders(LOGIN_ID, PASSWORD);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }
}
