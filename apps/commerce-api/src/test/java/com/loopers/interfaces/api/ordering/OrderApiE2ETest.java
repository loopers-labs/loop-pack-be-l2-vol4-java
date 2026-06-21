package com.loopers.interfaces.api.ordering;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.application.coupon.CouponCommand;
import com.loopers.application.coupon.CouponCommandService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.domain.ordering.order.OrderStatus;
import com.loopers.domain.payment.payment.PaymentStatus;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final CouponCommandService couponCommandService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandRepository brandRepository,
        ProductRepository productRepository,
        CouponCommandService couponCommandService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.couponCommandService = couponCommandService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class PlaceOrder {

        @DisplayName("유저 헤더와 주문 항목을 보내면 PAYMENT_PENDING 주문과 REQUESTED 결제 상태를 조회할 수 있다.")
        @Test
        void createsOrderAndReturnsDetailWithRequestedPaymentStatus() {
            // arrange
            Product product = saveProduct("상품", 1_000L, 10);

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderCreateResponse>> createResponse = placeOrder(
                "user1",
                new OrderDto.OrderCreateRequest(List.of(new OrderDto.OrderCreateItemRequest(product.getId(), 2)))
            );
            Long orderId = createResponse.getBody().data().orderId();

            ParameterizedTypeReference<ApiResponse<OrderDto.OrderDetailResponse>> detailType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderDto.OrderDetailResponse>> detailResponse = testRestTemplate.exchange(
                "/api/v1/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(userHeaders("user1")),
                detailType
            );

            Product changedProduct = productRepository.find(product.getId()).orElseThrow();

            // assert
            assertAll(
                () -> assertTrue(createResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(createResponse.getBody().data().orderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING),
                () -> assertThat(createResponse.getBody().data().originalAmount()).isEqualTo(2_000L),
                () -> assertThat(createResponse.getBody().data().discountAmount()).isZero(),
                () -> assertThat(createResponse.getBody().data().finalAmount()).isEqualTo(2_000L),
                () -> assertTrue(detailResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(detailResponse.getBody().data().paymentStatus()).isEqualTo(PaymentStatus.REQUESTED),
                () -> assertThat(detailResponse.getBody().data().items()).hasSize(1),
                () -> assertThat(detailResponse.getBody().data().items().get(0).productName()).isEqualTo("상품"),
                () -> assertThat(changedProduct.getStockQuantity()).isEqualTo(8)
            );
        }

        @DisplayName("발급 쿠폰 ID를 보내면 할인 금액과 최종 결제 금액을 반환한다.")
        @Test
        void appliesIssuedCoupon() {
            Product product = saveProduct("상품", 2_000L, 10);
            Long couponTemplateId = couponCommandService.createTemplate(new CouponCommand.CreateTemplate(
                "500원 할인",
                CouponType.FIXED,
                500L,
                null,
                1,
                ZonedDateTime.now().plusDays(1)
            )).couponId();
            Long issuedCouponId = couponCommandService.issue(couponTemplateId, "user1").couponId();

            ResponseEntity<ApiResponse<OrderDto.OrderCreateResponse>> response = placeOrder(
                "user1",
                new OrderDto.OrderCreateRequest(
                    List.of(new OrderDto.OrderCreateItemRequest(product.getId(), 1)),
                    issuedCouponId
                )
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().originalAmount()).isEqualTo(2_000L),
                () -> assertThat(response.getBody().data().discountAmount()).isEqualTo(500L),
                () -> assertThat(response.getBody().data().finalAmount()).isEqualTo(1_500L),
                () -> assertThat(response.getBody().data().couponId()).isEqualTo(issuedCouponId)
            );
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("로그인 사용자 본인의 주문만 기간 조건으로 조회한다.")
        @Test
        void returnsOnlyLoginUsersOrders() {
            // arrange
            Product product = saveProduct("상품", 1_000L, 10);
            placeOrder("user1", new OrderDto.OrderCreateRequest(List.of(new OrderDto.OrderCreateItemRequest(product.getId(), 1))));
            placeOrder("user2", new OrderDto.OrderCreateRequest(List.of(new OrderDto.OrderCreateItemRequest(product.getId(), 1))));

            // act
            ParameterizedTypeReference<ApiResponse<List<OrderDto.OrderListItemResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<OrderDto.OrderListItemResponse>>> response = testRestTemplate.exchange(
                "/api/v1/orders?startAt=" + LocalDate.now().minusDays(1) + "&endAt=" + LocalDate.now().plusDays(1),
                HttpMethod.GET,
                new HttpEntity<>(userHeaders("user1")),
                responseType
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).paymentStatus()).isEqualTo(PaymentStatus.REQUESTED)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    class GetAdminOrders {

        @DisplayName("어드민 헤더가 있으면 전체 주문 목록을 userId와 함께 조회한다.")
        @Test
        void returnsAllOrdersWithUserId_whenAdminHeaderIsValid() {
            // arrange
            Product product = saveProduct("상품", 1_000L, 10);
            placeOrder("user1", new OrderDto.OrderCreateRequest(List.of(new OrderDto.OrderCreateItemRequest(product.getId(), 1))));
            placeOrder("user2", new OrderDto.OrderCreateRequest(List.of(new OrderDto.OrderCreateItemRequest(product.getId(), 1))));

            // act
            ParameterizedTypeReference<ApiResponse<PageResponse<OrderAdminDto.OrderListItemResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<OrderAdminDto.OrderListItemResponse>>> response =
                testRestTemplate.exchange(
                    "/api-admin/v1/orders?page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders("loopers.admin")),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().pageInfo().totalElements()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().items()).extracting(OrderAdminDto.OrderListItemResponse::userId)
                    .containsExactlyInAnyOrder("user1", "user2"),
                () -> assertThat(response.getBody().data().items()).extracting(OrderAdminDto.OrderListItemResponse::paymentStatus)
                    .containsOnly(PaymentStatus.REQUESTED)
            );
        }

        @DisplayName("어드민 헤더가 올바르지 않으면 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenAdminHeaderIsInvalid() {
            // act
            ParameterizedTypeReference<ApiResponse<PageResponse<OrderAdminDto.OrderListItemResponse>>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<OrderAdminDto.OrderListItemResponse>>> response =
                testRestTemplate.exchange(
                    "/api-admin/v1/orders?page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders("invalid.admin")),
                    responseType
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private Product saveProduct(String name, Long price, Integer stockQuantity) {
        Brand brand = brandRepository.save(new Brand("Loopers", "테스트 브랜드"));
        return productRepository.save(new Product(brand.getId(), name, "설명", price, stockQuantity));
    }

    private ResponseEntity<ApiResponse<OrderDto.OrderCreateResponse>> placeOrder(
        String userId,
        OrderDto.OrderCreateRequest request
    ) {
        ParameterizedTypeReference<ApiResponse<OrderDto.OrderCreateResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            new HttpEntity<>(request, userHeaders(userId)),
            responseType
        );
    }

    private HttpHeaders userHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HeaderValidator.LOGIN_ID, userId);
        headers.add(HeaderValidator.LOGIN_PW, "password");
        return headers;
    }

    private HttpHeaders adminHeaders(String ldap) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HeaderValidator.ADMIN_LDAP, ldap);
        return headers;
    }
}
