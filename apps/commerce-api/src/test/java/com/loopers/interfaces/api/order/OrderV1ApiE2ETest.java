package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/orders";
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser() {
        return userRepository.save(new UserModel(LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));
    }

    private ProductModel saveProduct(String name, BigDecimal price) {
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        return productRepository.save(new ProductModel(brand.getId(), name, price));
    }

    private void saveStock(Long productId, Long quantity) {
        stockRepository.save(new StockModel(productId, quantity));
    }

    private CouponTemplateModel saveTemplate(BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        return couponTemplateRepository.save(
                new CouponTemplateModel("테스트 쿠폰", CouponType.FIXED, BigDecimal.valueOf(1000), minOrderAmount, expiredAt));
    }

    private IssuedCouponModel saveIssuedCoupon(Long couponTemplateId, Long userId) {
        return issuedCouponRepository.save(new IssuedCouponModel(couponTemplateId, userId));
    }

    private HttpEntity<Void> authHeaderEntity(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        headers.set(AuthHeaders.LOGIN_PW, loginPw);
        return new HttpEntity<>(null, headers);
    }

    private <T> HttpEntity<T> authJsonEntity(T body, String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        headers.set(AuthHeaders.LOGIN_PW, loginPw);
        return new HttpEntity<>(body, headers);
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 요청이면 주문 응답과 함께 재고가 차감되고 주문이 저장된다.")
        @Test
        void returnsOrderResponse_andDecreasesStock_whenValidRequest() {
            // given
            saveUser();
            ProductModel product = saveProduct("테스트 상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2L)), null
            );

            // when
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(request, LOGIN_ID, LOGIN_PW), responseType);

            // then
            Long orderId = response.getBody().data().id();
            StockModel stock = stockRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PLACED"),
                    () -> assertThat(response.getBody().data().originalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualByComparingTo(BigDecimal.ZERO),
                    () -> assertThat(response.getBody().data().finalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(response.getBody().data().items()).hasSize(1),
                    () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().items().get(0).subtotal()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(stock.getQuantity()).isEqualTo(3L),
                    () -> assertThat(orderRepository.findById(orderId)).isPresent()
            );
        }

        @DisplayName("items가 빈 리스트이면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenItemsIsEmpty() {
            // given
            saveUser();
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(List.of(), null);

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(request, LOGIN_ID, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 상품 ID이면 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // given
            saveUser();
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(999L, 1L)), null
            );

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(request, LOGIN_ID, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("재고가 부족하면 400 Bad Request를 반환하고 재고는 변경되지 않는다.")
        @Test
        void returnsBadRequest_whenStockIsInsufficient() {
            // given
            saveUser();
            ProductModel product = saveProduct("테스트 상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 1L);
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2L)), null
            );

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(request, LOGIN_ID, LOGIN_PW), Void.class);

            // then
            StockModel stock = stockRepository.findByProductId(product.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(stock.getQuantity()).isEqualTo(1L)
            );
        }

        @DisplayName("LOGIN_ID 헤더가 없으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenLoginIdHeaderIsMissing() {
            // given
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(1L, 1L)), null
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, new HttpEntity<>(request, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("LOGIN_PW 헤더가 없으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenLoginPwHeaderIsMissing() {
            // given
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(1L, 1L)), null
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, new HttpEntity<>(request, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("기간 내 주문이 있으면 주문 목록을 반환한다.")
        @Test
        void returnsOrderList_whenOrdersExistInPeriod() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            testRestTemplate.exchange(BASE_URL, HttpMethod.POST,
                    authJsonEntity(new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), null), LOGIN_ID, LOGIN_PW),
                    Void.class);

            String today = LocalDate.now().toString();
            String yesterday = LocalDate.now().minusDays(1).toString();

            // when
            ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response =
                    testRestTemplate.exchange(BASE_URL + "?startAt=" + yesterday + "&endAt=" + today,
                            HttpMethod.GET, authHeaderEntity(LOGIN_ID, LOGIN_PW), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data()).hasSize(1)
            );
        }

        @DisplayName("기간 내 주문이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoOrdersInPeriod() {
            // given
            saveUser();
            String yesterday = LocalDate.now().minusDays(1).toString();
            String twoDaysAgo = LocalDate.now().minusDays(2).toString();

            // when
            ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<OrderV1Dto.OrderResponse>>> response =
                    testRestTemplate.exchange(BASE_URL + "?startAt=" + twoDaysAgo + "&endAt=" + yesterday,
                            HttpMethod.GET, authHeaderEntity(LOGIN_ID, LOGIN_PW), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("startAt이 endAt보다 이후이면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenStartAtIsAfterEndAt() {
            // given
            saveUser();
            String today = LocalDate.now().toString();
            String yesterday = LocalDate.now().minusDays(1).toString();

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "?startAt=" + today + "&endAt=" + yesterday,
                            HttpMethod.GET, authHeaderEntity(LOGIN_ID, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("LOGIN_ID 헤더가 없으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenLoginIdHeaderIsMissing() {
            // given
            String today = LocalDate.now().toString();
            String yesterday = LocalDate.now().minusDays(1).toString();
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "?startAt=" + yesterday + "&endAt=" + today,
                            HttpMethod.GET, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("본인 주문을 조회하면 주문 상세 정보를 반환한다.")
        @Test
        void returnsOrderResponse_whenOwnerRequests() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createResponse =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST,
                            authJsonEntity(new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), null), LOGIN_ID, LOGIN_PW),
                            responseType);
            Long orderId = createResponse.getBody().data().id();

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange(BASE_URL + "/" + orderId,
                            HttpMethod.GET, authHeaderEntity(LOGIN_ID, LOGIN_PW), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PLACED"),
                    () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("다른 사용자의 주문을 조회하면 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenAccessingAnotherUsersOrder() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createResponse =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST,
                            authJsonEntity(new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), null), LOGIN_ID, LOGIN_PW),
                            responseType);
            Long orderId = createResponse.getBody().data().id();

            String otherId = "other01";
            userRepository.save(new UserModel(otherId, LOGIN_PW, "다른유저", "1991-01-01", "other@example.com", Gender.FEMALE, passwordEncryptor));

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "/" + orderId,
                            HttpMethod.GET, authHeaderEntity(otherId, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("존재하지 않는 주문 ID이면 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // given
            saveUser();

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "/999",
                            HttpMethod.GET, authHeaderEntity(LOGIN_ID, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("LOGIN_ID 헤더가 없으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenLoginIdHeaderIsMissing() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "/1",
                            HttpMethod.GET, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("POST /api/v1/orders - 쿠폰 적용")
    @Nested
    class CreateOrderWithCoupon {

        @DisplayName("유효한 쿠폰을 적용하면 할인이 적용된 주문 응답을 반환하고 쿠폰이 USED로 변경된다.")
        @Test
        void returnsDiscountedOrderResponse_andCouponIsUsed_whenValidCouponApplied() {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 2L);

            CouponTemplateModel template = saveTemplate(null, ZonedDateTime.now().plusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), user.getId());

            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2L)), issued.getId()
            );

            // when
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(request, LOGIN_ID, LOGIN_PW), responseType);

            // then
            IssuedCouponModel usedCoupon = issuedCouponRepository.findById(issued.getId()).orElseThrow();
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().originalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(response.getBody().data().discountAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000)),
                    () -> assertThat(response.getBody().data().finalPrice()).isEqualByComparingTo(BigDecimal.valueOf(19000)),
                    () -> assertThat(usedCoupon.getStatus()).isEqualTo(com.loopers.domain.coupon.CouponStatus.USED)
            );
        }

        @DisplayName("만료된 쿠폰을 적용하면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenCouponIsExpired() {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 1L);

            CouponTemplateModel template = saveTemplate(null, ZonedDateTime.now().minusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), user.getId());

            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), issued.getId()
            );

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(request, LOGIN_ID, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("최소 주문 금액을 충족하지 않으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenMinOrderAmountNotMet() {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(5000));
            saveStock(product.getId(), 1L);

            CouponTemplateModel template = saveTemplate(BigDecimal.valueOf(10000), ZonedDateTime.now().plusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), user.getId());

            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), issued.getId()
            );

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(request, LOGIN_ID, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("다른 사용자의 쿠폰을 적용하면 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenCouponOwnedByAnotherUser() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 1L);

            String otherId = "other01";
            UserModel otherUser = userRepository.save(new UserModel(otherId, LOGIN_PW, "다른유저", "1991-01-01", "other@example.com", Gender.FEMALE, passwordEncryptor));
            CouponTemplateModel template = saveTemplate(null, ZonedDateTime.now().plusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), otherUser.getId());

            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), issued.getId()
            );

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(request, LOGIN_ID, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("존재하지 않는 issuedCouponId로 주문하면 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenIssuedCouponDoesNotExist() {
            // given
            saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 1L);

            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), 999L
            );

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(request, LOGIN_ID, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("이미 사용된 쿠폰을 적용하면 409 Conflict를 반환한다.")
        @Test
        void returnsConflict_whenCouponAlreadyUsed() {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct("상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 10L);

            CouponTemplateModel template = saveTemplate(null, ZonedDateTime.now().plusDays(1));
            IssuedCouponModel issued = saveIssuedCoupon(template.getId(), user.getId());

            OrderV1Dto.CreateRequest firstRequest = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), issued.getId()
            );
            testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(firstRequest, LOGIN_ID, LOGIN_PW), Void.class);

            OrderV1Dto.CreateRequest secondRequest = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), issued.getId()
            );

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL, HttpMethod.POST, authJsonEntity(secondRequest, LOGIN_ID, LOGIN_PW), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }
}
