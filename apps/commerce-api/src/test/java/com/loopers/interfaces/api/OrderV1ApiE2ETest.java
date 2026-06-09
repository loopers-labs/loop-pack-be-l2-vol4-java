package com.loopers.interfaces.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponType;
import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.fixture.UserFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.support.pg.PgHmacVerifier;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String USERS_URL    = "/api/v1/users";
    private static final String BRANDS_URL   = "/api-admin/v1/brands";
    private static final String PRODUCTS_URL = "/api-admin/v1/products";
    private static final String ORDERS_URL   = "/api/v1/orders";
    private static final String PAYMENTS_CONFIRM_URL = "/api-webhook/v1/payments/confirm";
    private static final String PAYMENTS_FAIL_URL    = "/api-webhook/v1/payments/fail";
    private static final String ADMIN_COUPONS_URL    = "/api-admin/v1/coupons";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${pg.hmac-secret}")
    private String pgHmacSecret;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UUID userId;
    private UUID productId;
    private UUID brandId;

    @BeforeEach
    void setUp() {
        ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> userResp = testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<>() {}
        );
        assertThat(userResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        userId = userResp.getBody().data().id();

        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> brandResp = testRestTemplate.exchange(
            BRANDS_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        brandId = brandResp.getBody().data().id();

        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> productResp = testRestTemplate.exchange(
            PRODUCTS_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
            ), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        productId = productResp.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
        headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    /** PG 콜백용 — HMAC 서명 + JSON Content-Type 헤더 */
    private HttpHeaders pgHeaders(String rawBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-PG-Signature", PgHmacVerifier.computeHmac(rawBody, pgHmacSecret));
        return headers;
    }

    /** 요청 객체를 JSON 직렬화 — HMAC 계산용 */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private OrderV1Dto.CreateRequest validCreateRequest() {
        OrderV1Dto.ShippingInfoRequest shipping = new OrderV1Dto.ShippingInfoRequest(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", "101호"
        );
        return new OrderV1Dto.CreateRequest(shipping, List.of(new OrderV1Dto.OrderItemRequest(productId, 2)));
    }

    /** 쿠폰 템플릿 생성(admin) → templateId */
    private UUID createCouponTemplate(CouponType type, long value, Long minOrderAmount) {
        ResponseEntity<ApiResponse<CouponV1Dto.TemplateResponse>> resp = testRestTemplate.exchange(
            ADMIN_COUPONS_URL, HttpMethod.POST,
            new HttpEntity<>(new CouponV1Dto.CreateRequest("쿠폰" + UUID.randomUUID(), type, value, minOrderAmount,
                LocalDateTime.now().plusDays(30)), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().data().id();
    }

    /** 본인에게 발급 → userCouponId */
    private UUID issueCoupon(UUID templateId) {
        ResponseEntity<ApiResponse<CouponV1Dto.UserCouponResponse>> resp = testRestTemplate.exchange(
            "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST,
            new HttpEntity<>(authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().data().id();
    }

    private OrderV1Dto.CreateRequest createRequestWithCoupon(int quantity, UUID couponId) {
        OrderV1Dto.ShippingInfoRequest shipping = new OrderV1Dto.ShippingInfoRequest(
            "홍길동", "010-1234-5678", "12345", "서울시", "101호"
        );
        return new OrderV1Dto.CreateRequest(shipping, List.of(new OrderV1Dto.OrderItemRequest(productId, quantity)), couponId);
    }

    /** 지정 재고로 상품 생성(admin) → productId */
    private UUID createProduct(int quantity) {
        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> resp = testRestTemplate.exchange(
            PRODUCTS_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, "상품" + UUID.randomUUID(), ProductFixture.DESCRIPTION, ProductFixture.PRICE, quantity
            ), adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().data().id();
    }

    private OrderV1Dto.CreateRequest orderRequest(UUID pid, int quantity) {
        OrderV1Dto.ShippingInfoRequest shipping = new OrderV1Dto.ShippingInfoRequest(
            "홍길동", "010-1234-5678", "12345", "서울시", "101호"
        );
        return new OrderV1Dto.CreateRequest(shipping, List.of(new OrderV1Dto.OrderItemRequest(pid, quantity)), null);
    }

    @DisplayName("POST /api/v1/orders — 쿠폰 적용")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("정액 쿠폰 적용 시, 200 + 할인액·최종금액이 반영된다.")
        @Test
        void appliesFixedCoupon() {
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, null);
            UUID couponId = issueCoupon(templateId);
            long original = ProductFixture.PRICE * 2;

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().originalAmount()).isEqualTo(original),
                () -> assertThat(response.getBody().data().discountAmount()).isEqualTo(3000L),
                () -> assertThat(response.getBody().data().pgAmount()).isEqualTo(original - 3000L),
                () -> assertThat(response.getBody().data().couponId()).isEqualTo(couponId)
            );
        }

        @DisplayName("존재하지 않는 쿠폰으로 주문 시, 404 를 반환한다.")
        @Test
        void returnsNotFound_whenCouponNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, UUID.randomUUID()), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("이미 사용된 쿠폰으로 재주문 시, 409 를 반환한다.")
        @Test
        void returnsConflict_whenCouponAlreadyUsed() {
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, null);
            UUID couponId = issueCoupon(templateId);
            testRestTemplate.exchange(ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), authHeaders()),
                new ParameterizedTypeReference<Void>() {});

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("최소 주문 금액 미달 쿠폰으로 주문 시, 409 를 반환한다.")
        @Test
        void returnsConflict_whenBelowMinOrderAmount() {
            long original = ProductFixture.PRICE * 2;
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, original + 1);
            UUID couponId = issueCoupon(templateId);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("결제 실패 시, 쿠폰이 복구되어 동일 쿠폰으로 재주문할 수 있다.")
        @Test
        void releasesCoupon_whenPaymentFails() {
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, null);
            UUID couponId = issueCoupon(templateId);

            // 1차 주문 — 쿠폰 사용
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount = created.getBody().data().pgAmount();

            // 결제 실패 웹훅 → 쿠폰 복구
            String failBody = toJson(new PaymentV1Dto.FailRequest(orderId, "pg-tx-fail-001", amount));
            testRestTemplate.exchange(
                PAYMENTS_FAIL_URL, HttpMethod.POST,
                new HttpEntity<>(failBody, pgHeaders(failBody)),
                new ParameterizedTypeReference<Void>() {}
            );

            // 같은 쿠폰으로 재주문 → 성공 (복구 확인)
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> reorder = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(reorder.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("쿠폰 적용 주문을 취소하면, 쿠폰이 복구되어 재사용 가능하다.")
        @Test
        void releasesCoupon_whenCancelled() {
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, null);
            UUID couponId = issueCoupon(templateId);

            // 주문(쿠폰 사용) → 결제 확정(CONFIRMED)
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount = created.getBody().data().pgAmount();
            String confirmBody = toJson(new PaymentV1Dto.ConfirmRequest(orderId, "pg-tx-cancel", amount));
            testRestTemplate.exchange(PAYMENTS_CONFIRM_URL, HttpMethod.POST,
                new HttpEntity<>(confirmBody, pgHeaders(confirmBody)), new ParameterizedTypeReference<Void>() {});

            // 주문 취소 → 쿠폰 복구
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> cancelled = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {}
            );
            assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 같은 쿠폰으로 재주문 → 성공 (복구 확인)
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> reorder = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(createRequestWithCoupon(2, couponId), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(reorder.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("동시성 — 동일 쿠폰 동시 주문")
    @Nested
    class ConcurrentCouponOrder {

        @DisplayName("동일 쿠폰으로 여러 기기에서 동시 주문해도, 쿠폰은 단 한번만 사용된다.")
        @Test
        void couponUsedOnce_underConcurrency() throws InterruptedException {
            UUID templateId = createCouponTemplate(CouponType.FIXED, 3000L, null);
            UUID couponId = issueCoupon(templateId);

            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger conflict = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        ResponseEntity<ApiResponse<Void>> r = testRestTemplate.exchange(
                            ORDERS_URL, HttpMethod.POST,
                            new HttpEntity<>(createRequestWithCoupon(1, couponId), authHeaders()),
                            new ParameterizedTypeReference<>() {}
                        );
                        if (r.getStatusCode() == HttpStatus.OK) success.incrementAndGet();
                        else if (r.getStatusCode() == HttpStatus.CONFLICT) conflict.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            ready.await();
            start.countDown(); // 동시 출발
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            assertAll(
                () -> assertThat(success.get()).isEqualTo(1),
                () -> assertThat(conflict.get()).isEqualTo(threads - 1)
            );
        }
    }

    @DisplayName("동시성 — 동일 상품 동시 주문(재고 차감)")
    @Nested
    class ConcurrentStockOrder {

        @DisplayName("재고 5개 상품에 10명이 동시 주문해도, 정확히 5건만 성공하고 오버셀이 없다.")
        @Test
        void noOversell_underConcurrency() throws InterruptedException {
            int stock = 5;
            int threads = 10;
            UUID pid = createProduct(stock);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger conflict = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        ResponseEntity<ApiResponse<Void>> r = testRestTemplate.exchange(
                            ORDERS_URL, HttpMethod.POST,
                            new HttpEntity<>(orderRequest(pid, 1), authHeaders()),
                            new ParameterizedTypeReference<>() {}
                        );
                        if (r.getStatusCode() == HttpStatus.OK) success.incrementAndGet();
                        else if (r.getStatusCode() == HttpStatus.CONFLICT) conflict.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            ready.await();
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            assertAll(
                () -> assertThat(success.get()).isEqualTo(stock),
                () -> assertThat(conflict.get()).isEqualTo(threads - stock)
            );
        }
    }

    @DisplayName("동시성 — 중복 결제확정 콜백")
    @Nested
    class ConcurrentConfirm {

        @DisplayName("동일 주문에 confirm 콜백이 동시에 여러번 와도, 재고는 한번만 차감된다.")
        @Test
        void stockConfirmedOnce_underDuplicateCallback() throws InterruptedException {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount = created.getBody().data().pgAmount();
            String body = toJson(new PaymentV1Dto.ConfirmRequest(orderId, "pg-tx-dup", amount));

            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        testRestTemplate.exchange(PAYMENTS_CONFIRM_URL, HttpMethod.POST,
                            new HttpEntity<>(body, pgHeaders(body)), new ParameterizedTypeReference<Void>() {});
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> detail = testRestTemplate.exchange(
                "/api-admin/v1/products/" + productId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), new ParameterizedTypeReference<>() {}
            );
            assertAll(
                () -> assertThat(detail.getBody().data().totalQuantity()).isEqualTo(ProductFixture.INITIAL_QUANTITY - 2),
                () -> assertThat(detail.getBody().data().reservedQuantity()).isEqualTo(0)
            );
        }
    }

    @DisplayName("POST /api/v1/orders — 주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 요청이면, 200 + PENDING 주문을 반환한다.")
        @Test
        void returnsOrder_whenValid() {
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("PENDING"),
                () -> assertThat(response.getBody().data().pgAmount()).isEqualTo(ProductFixture.PRICE * 2),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("인증 헤더 없이 요청 시, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenNoAuth() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("재고가 부족하면, 409를 반환한다.")
        @Test
        void returnsConflict_whenStockInsufficient() {
            OrderV1Dto.CreateRequest req = new OrderV1Dto.CreateRequest(
                new OrderV1Dto.ShippingInfoRequest("홍길동", "010-1234-5678", "12345", "서울시", null),
                List.of(new OrderV1Dto.OrderItemRequest(productId, ProductFixture.INITIAL_QUANTITY + 1))
            );

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(req, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("존재하지 않는 상품이면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExists() {
            OrderV1Dto.CreateRequest req = new OrderV1Dto.CreateRequest(
                new OrderV1Dto.ShippingInfoRequest("홍길동", "010-1234-5678", "12345", "서울시", null),
                List.of(new OrderV1Dto.OrderItemRequest(UUID.randomUUID(), 1))
            );

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(req, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} — 주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문 조회 시, 200 + 주문 정보를 반환한다.")
        @Test
        void returnsOrder_whenOwner() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(orderId)
            );
        }

        @DisplayName("존재하지 않는 주문 ID면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenNotExists() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders — 주문 목록 조회")
    @Nested
    class GetOrderList {

        @DisplayName("본인 주문 목록 조회 시, 200 + 목록을 반환한다.")
        @Test
        void returnsList_whenOwnUser() {
            // arrange
            testRestTemplate.exchange(ORDERS_URL, HttpMethod.POST, new HttpEntity<>(validCreateRequest(), authHeaders()), new ParameterizedTypeReference<>() {});

            String startAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endAt   = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String url = ORDERS_URL + "?userId=" + userId + "&startAt=" + startAt + "&endAt=" + endAt + "&page=0&size=10";

            // act
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderResponse>>> response = testRestTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(1)
            );
        }

        @DisplayName("타인의 주문 목록 조회 시, 404를 반환한다.")
        @Test
        void returnsNotFound_whenOtherUser() {
            String startAt = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String endAt   = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String url = ORDERS_URL + "?userId=" + UUID.randomUUID() + "&startAt=" + startAt + "&endAt=" + endAt + "&page=0&size=10";

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                url, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api/v1/orders/{orderId}/cancel — 주문 취소")
    @Nested
    class CancelOrder {

        @DisplayName("CONFIRMED 주문 취소 시, 200 + CANCELLED 상태를 반환한다.")
        @Test
        void cancelsOrder_whenConfirmed() {
            // arrange — 주문 생성 → 결제 확정
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount  = created.getBody().data().pgAmount();

            String confirmBody = toJson(new PaymentV1Dto.ConfirmRequest(orderId, "pg-tx-001", amount));
            testRestTemplate.exchange(
                PAYMENTS_CONFIRM_URL, HttpMethod.POST,
                new HttpEntity<>(confirmBody, pgHeaders(confirmBody)),
                new ParameterizedTypeReference<>() {}
            );

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("CANCELLED")
            );
        }

        @DisplayName("PENDING 주문 취소 시, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenPending() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api-webhook/v1/payments/confirm — 결제 확정")
    @Nested
    class ConfirmPayment {

        @DisplayName("올바른 HMAC + 금액이면, 200 + SUCCESS 결제를 반환한다.")
        @Test
        void confirmsPayment_whenAmountMatches() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount  = created.getBody().data().pgAmount();
            String rawBody = toJson(new PaymentV1Dto.ConfirmRequest(orderId, "pg-tx-001", amount));

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_CONFIRM_URL, HttpMethod.POST,
                new HttpEntity<>(rawBody, pgHeaders(rawBody)),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("SUCCESS"),
                () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId)
            );
        }

        @DisplayName("서명 없이 요청하면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenSignatureMissing() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount  = created.getBody().data().pgAmount();
            String rawBody = toJson(new PaymentV1Dto.ConfirmRequest(orderId, "pg-tx-001", amount));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // act — 서명 헤더 없음
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_CONFIRM_URL, HttpMethod.POST,
                new HttpEntity<>(rawBody, headers),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("금액 불일치이면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenAmountMismatch() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            String rawBody = toJson(new PaymentV1Dto.ConfirmRequest(orderId, "pg-tx-001", 1L));

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                PAYMENTS_CONFIRM_URL, HttpMethod.POST,
                new HttpEntity<>(rawBody, pgHeaders(rawBody)),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api-webhook/v1/payments/fail — 결제 실패")
    @Nested
    class FailPayment {

        @DisplayName("PENDING 주문 결제 실패 시, 200 + FAILED 결제를 반환하고 주문이 FAILED 된다.")
        @Test
        void failsPayment_whenPending() {
            // arrange
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> created = testRestTemplate.exchange(
                ORDERS_URL, HttpMethod.POST,
                new HttpEntity<>(validCreateRequest(), authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            UUID orderId = created.getBody().data().id();
            Long amount  = created.getBody().data().pgAmount();
            String rawBody = toJson(new PaymentV1Dto.FailRequest(orderId, "pg-tx-fail-001", amount));

            // act
            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                PAYMENTS_FAIL_URL, HttpMethod.POST,
                new HttpEntity<>(rawBody, pgHeaders(rawBody)),
                new ParameterizedTypeReference<>() {}
            );

            // assert — 결제 FAILED, 주문 FAILED 확인
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().status().name()).isEqualTo("FAILED")
            );

            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResp = testRestTemplate.exchange(
                ORDERS_URL + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(orderResp.getBody().data().status().name()).isEqualTo("FAILED");
        }
    }
}
