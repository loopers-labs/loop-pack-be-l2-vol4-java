package com.loopers.interfaces.api;

import com.loopers.application.user.UserService;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.interfaces.api.order.OrderDto;
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
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiE2ETest {

    private static final String BASE_URL = "/api/v1/orders";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UserModel savedUser;
    private ProductModel savedProduct;
    private HttpHeaders userHeaders;

    @BeforeEach
    void setUp() {
        savedUser = userService.signUp(new UserModel(
            "user01", "Password1!", "홍길동",
            LocalDate.of(1990, 1, 1), "user@example.com"
        ));
        savedProduct = productJpaRepository.save(new ProductModel("에어포스1", 10000L, 1L));
        stockJpaRepository.save(new StockModel(savedProduct.getId(), 10));

        userHeaders = new HttpHeaders();
        userHeaders.set(LOGIN_ID_HEADER, "user01");
        userHeaders.set(LOGIN_PW_HEADER, "Password1!");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("정상 주문 생성 시, 201과 PENDING 상태를 반환한다.")
        @Test
        void returns201_whenOrderCreatedSuccessfully() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 2))
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().status()).isEqualTo("PENDING");
        }

        @DisplayName("비로그인 상태로 요청하면, 401을 반환한다.")
        @Test
        void returns401_whenNotLoggedIn() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1))
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 상품으로 주문하면, 404를 반환한다.")
        @Test
        void returns404_whenProductNotFound() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(999L, 1))
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면, 400을 반환한다.")
        @Test
        void returns400_whenStockIsInsufficient() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 999))
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/orders/{orderId}")
    @Nested
    class CancelOrder {

        @DisplayName("정상 주문 취소 시, 200을 반환하고 재고가 복구된다.")
        @Test
        void returns200_whenOrderCancelledSuccessfully() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 2))
            );
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );
            Long orderId = created.getBody().data().orderId();

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + orderId, HttpMethod.DELETE,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(stockJpaRepository.findByProductId(savedProduct.getId()).orElseThrow().getQuantity()).isEqualTo(10);
        }

        @DisplayName("다른 회원의 주문을 취소하면, 403을 반환한다.")
        @Test
        void returns403_whenNotOwner() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1))
            );
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );
            Long orderId = created.getBody().data().orderId();

            userService.signUp(new UserModel(
                "user02", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "other@example.com"
            ));
            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set(LOGIN_ID_HEADER, "user02");
            otherHeaders.set(LOGIN_PW_HEADER, "Password2@");

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + orderId, HttpMethod.DELETE,
                new HttpEntity<>(otherHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("이미 취소된 주문을 취소하면, 400을 반환한다.")
        @Test
        void returns400_whenAlreadyCancelled() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1))
            );
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );
            Long orderId = created.getBody().data().orderId();

            testRestTemplate.exchange(
                BASE_URL + "/" + orderId, HttpMethod.DELETE,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/" + orderId, HttpMethod.DELETE,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("내 주문 목록 조회 시, 200과 주문 목록을 반환한다.")
        @Test
        void returns200_withOrderList() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1))
            );
            testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // act
            ResponseEntity<ApiResponse<List<OrderDto.OrderResponse>>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data()).hasSize(1);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrderDetail {

        @DisplayName("주문 상세 조회 시, 200과 OrderItem 포함 주문 정보를 반환한다.")
        @Test
        void returns200_withOrderDetail() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1))
            );
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );
            Long orderId = created.getBody().data().orderId();

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().orderId()).isEqualTo(orderId);
            assertThat(response.getBody().data().items()).hasSize(1);
            assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("에어포스1");
        }
    }

    @DisplayName("POST /api/v1/orders (쿠폰 적용)")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("쿠폰 없이 주문 시 discountAmount=0, totalPrice=originalAmount이다.")
        @Test
        void noCoupon_discountIsZero() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1)), null
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().discountAmount()).isEqualTo(0L);
            assertThat(response.getBody().data().totalPrice()).isEqualTo(response.getBody().data().originalAmount());
        }

        @DisplayName("FIXED 쿠폰 적용 시 totalPrice = originalAmount - fixedAmount이다.")
        @Test
        void fixedCoupon_appliesDiscount() {
            // arrange
            CouponTemplateModel template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, java.time.LocalDateTime.now().plusDays(7)));
            UserCouponModel userCoupon = userCouponJpaRepository.save(
                new UserCouponModel(savedUser.getId(), template.getId()));

            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1)), userCoupon.getId()
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().discountAmount()).isEqualTo(1000L);
            assertThat(response.getBody().data().totalPrice()).isEqualTo(response.getBody().data().originalAmount() - 1000L);
        }

        @DisplayName("RATE 쿠폰 적용 시 totalPrice = originalAmount - (originalAmount × rate / 100)이다.")
        @Test
        void rateCoupon_appliesDiscount() {
            // arrange
            CouponTemplateModel template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("10% 할인", CouponType.RATE, 10L, null, java.time.LocalDateTime.now().plusDays(7)));
            UserCouponModel userCoupon = userCouponJpaRepository.save(
                new UserCouponModel(savedUser.getId(), template.getId()));

            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1)), userCoupon.getId()
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert: savedProduct.price=10000, 10% → discount=1000
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().discountAmount()).isEqualTo(1000L);
            assertThat(response.getBody().data().totalPrice()).isEqualTo(9000L);
        }

        @DisplayName("최소 주문 금액 미달 쿠폰 사용 시 400이 반환된다.")
        @Test
        void minOrderAmount_returns400_whenNotMet() {
            // arrange
            CouponTemplateModel template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, 50000L, java.time.LocalDateTime.now().plusDays(7)));
            UserCouponModel userCoupon = userCouponJpaRepository.save(
                new UserCouponModel(savedUser.getId(), template.getId()));

            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1)), userCoupon.getId()
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이미 사용된 쿠폰으로 주문 시 400이 반환된다.")
        @Test
        void usedCoupon_returns400() {
            // arrange
            CouponTemplateModel template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, java.time.LocalDateTime.now().plusDays(7)));
            UserCouponModel userCoupon = userCouponJpaRepository.save(
                new UserCouponModel(savedUser.getId(), template.getId()));
            userCoupon.use();
            userCouponJpaRepository.save(userCoupon);

            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1)), userCoupon.getId()
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("차단된 쿠폰으로 주문 시 400이 반환된다.")
        @Test
        void blockedCoupon_returns400() {
            // arrange
            CouponTemplateModel template = couponTemplateJpaRepository.save(
                new CouponTemplateModel("1000원 할인", CouponType.FIXED, 1000L, null, java.time.LocalDateTime.now().plusDays(7)));
            UserCouponModel userCoupon = userCouponJpaRepository.save(
                new UserCouponModel(savedUser.getId(), template.getId()));
            template.block();
            couponTemplateJpaRepository.save(template);

            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1)), userCoupon.getId()
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PATCH /api/v1/orders/{orderId}/confirm")
    @Nested
    class ConfirmOrder {

        @DisplayName("PENDING 주문 확정 시, 200과 CONFIRMED 상태를 반환한다.")
        @Test
        void returns200_whenOrderConfirmed() {
            // arrange
            OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                List.of(new OrderDto.OrderItemRequest(savedProduct.getId(), 1))
            );
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> created = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request, userHeaders),
                new ParameterizedTypeReference<>() {}
            );
            Long orderId = created.getBody().data().orderId();

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/" + orderId + "/confirm", HttpMethod.PATCH,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo("CONFIRMED");
        }
    }
}
