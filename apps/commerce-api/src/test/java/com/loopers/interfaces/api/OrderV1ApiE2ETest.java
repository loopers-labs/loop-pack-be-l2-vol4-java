package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final CouponJpaRepository couponJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long brandId;
    private Long userId;

    @Autowired
    public OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        CouponJpaRepository couponJpaRepository,
        UserCouponJpaRepository userCouponJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.couponJpaRepository = couponJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        UserModel user = userJpaRepository.save(
            new UserModel("tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M));
        this.userId = user.getId();
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "Just Do It"));
        this.brandId = brand.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long persistProduct(int stock) {
        return productJpaRepository.save(new ProductModel(brandId, "에어맥스", "운동화", 1000L, stock)).getId();
    }

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.HEADER_LOGIN_ID, loginId);
        headers.set(AuthHeaders.HEADER_LOGIN_PW, "Password1!");
        return headers;
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrder(String loginId, Long productId, int quantity) {
        return createOrderWithCoupon(loginId, productId, quantity, null);
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrderWithCoupon(
        String loginId, Long productId, int quantity, Long couponId
    ) {
        OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
            List.of(new OrderV1Dto.OrderItemRequest(productId, quantity)), couponId
        );
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders(loginId)), responseType
        );
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("정상 주문이면, 2xx 응답·총액 반환과 함께 재고가 차감된다.")
        @Test
        void createsOrderAndDecreasesStock() {
            // arrange
            Long productId = persistProduct(10);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder("tester01", productId, 2);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(2000L),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(productJpaRepository.findById(productId).orElseThrow().getStock()).isEqualTo(8)
            );
        }

        @DisplayName("재고가 부족하면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenStockInsufficient() {
            // arrange
            Long productId = persistProduct(1);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder("tester01", productId, 5);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 상품이면, 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductMissing() {
            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder("tester01", 99999L, 1);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 유저면, 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenUserMissing() {
            // arrange
            Long productId = persistProduct(10);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder("ghost", productId, 1);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api/v1/orders (쿠폰 적용)")
    @Nested
    class CreateOrderWithCoupon {

        private Coupon persistCoupon() {
            return couponJpaRepository.save(
                new Coupon("10% 할인", CouponType.RATE, 10L, null, java.time.ZonedDateTime.now().plusDays(30)));
        }

        @DisplayName("쿠폰을 적용하면, 금액 3종(총액/할인/최종)이 반환되고 쿠폰은 USED 가 된다.")
        @Test
        void appliesCoupon() {
            // arrange
            Long productId = persistProduct(10); // 단가 1000원
            Coupon coupon = persistCoupon();
            UserCoupon userCoupon = userCouponJpaRepository.save(
                coupon.issueTo(userId, java.time.ZonedDateTime.now()));

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                createOrderWithCoupon("tester01", productId, 2, userCoupon.getId());

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(2000L),
                () -> assertThat(response.getBody().data().discountAmount()).isEqualTo(200L),
                () -> assertThat(response.getBody().data().finalAmount()).isEqualTo(1800L),
                () -> assertThat(userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow().getStatus())
                    .isEqualTo(UserCouponStatus.USED),
                () -> assertThat(productJpaRepository.findById(productId).orElseThrow().getStock()).isEqualTo(8)
            );
        }

        @DisplayName("이미 사용된 쿠폰이면, 400 응답과 함께 재고 차감도 롤백된다.")
        @Test
        void rollsBackStock_whenCouponAlreadyUsed() {
            // arrange
            Long productId = persistProduct(10);
            Coupon coupon = persistCoupon();
            UserCoupon userCoupon = coupon.issueTo(userId, java.time.ZonedDateTime.now());
            userCoupon.use(userId, java.time.ZonedDateTime.now());
            Long usedCouponId = userCouponJpaRepository.save(userCoupon).getId();

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                createOrderWithCoupon("tester01", productId, 2, usedCouponId);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(productJpaRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10)
            );
        }

        @DisplayName("타 유저 소유의 쿠폰이면, 404 응답과 함께 재고 차감도 롤백된다.")
        @Test
        void rollsBackStock_whenCouponNotOwned() {
            // arrange
            Long productId = persistProduct(10);
            Coupon coupon = persistCoupon();
            Long othersCouponId = userCouponJpaRepository.save(
                coupon.issueTo(99999L, java.time.ZonedDateTime.now())).getId();

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                createOrderWithCoupon("tester01", productId, 2, othersCouponId);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(productJpaRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10)
            );
        }

        @DisplayName("존재하지 않는 쿠폰이면, 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenCouponMissing() {
            // arrange
            Long productId = persistProduct(10);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                createOrderWithCoupon("tester01", productId, 1, 99999L);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문이면, 2xx 응답과 주문 정보를 반환한다.")
        @Test
        void returnsOrder_whenOwner() {
            // arrange
            Long productId = persistProduct(10);
            Long orderId = createOrder("tester01", productId, 2).getBody().data().id();

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + orderId, HttpMethod.GET, new HttpEntity<>(authHeaders("tester01")), responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().id()).isEqualTo(orderId),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(2000L)
            );
        }

        @DisplayName("타 유저의 주문을 조회하면, 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenNotOwner() {
            // arrange
            userJpaRepository.save(new UserModel("tester02", "Password2!", "김철수", "1992-03-03", "other@example.com", Gender.M));
            Long productId = persistProduct(10);
            Long orderId = createOrder("tester01", productId, 2).getBody().data().id();

            // act
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + orderId, HttpMethod.GET, new HttpEntity<>(authHeaders("tester02")), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
