package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

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

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final String RAW_PASSWORD = "Kyle!2030";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderItemJpaRepository orderItemJpaRepository;

    @Autowired
    private CouponJpaRepository couponJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private PasswordEncrypter passwordEncrypter;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser(String loginId) {
        return userJpaRepository.save(UserModel.builder()
            .rawLoginId(loginId)
            .rawPassword(RAW_PASSWORD)
            .rawName("테스트유저")
            .rawBirthDate(LocalDate.of(1995, 3, 21))
            .rawEmail(loginId + "@example.com")
            .passwordEncrypter(passwordEncrypter)
            .build());
    }

    private BrandModel saveBrand(String name) {
        return brandJpaRepository.save(BrandModel.builder()
            .rawName(name)
            .rawDescription("감성을 담은 브랜드")
            .build());
    }

    private ProductModel saveProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(ProductModel.builder()
            .brandId(brandId)
            .rawName(name)
            .rawDescription("포근한 감성 가디건")
            .rawPrice(price)
            .rawStock(stock)
            .build());
    }

    private OrderModel saveOrder(Long userId, Long productId) {
        OrderModel savedOrder = orderJpaRepository.save(OrderModel.builder()
            .userId(userId)
            .orderedAt(ZonedDateTime.now())
            .originalAmount(78_000)
            .discountAmount(0)
            .finalAmount(78_000)
            .build());

        OrderItemModel orderItem = OrderItemModel.builder()
            .productId(productId)
            .productName("감성 가디건")
            .productBrandName("감성 브랜드")
            .unitPrice(39_000)
            .rawQuantity(2)
            .build();
        orderItem.assignOrder(savedOrder.getId());
        orderItemJpaRepository.save(orderItem);

        return savedOrder;
    }

    private HttpEntity<Object> memberJsonRequest(String loginId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);

        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> memberGet(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LOGIN_ID_HEADER, loginId);
        headers.add(LOGIN_PW_HEADER, RAW_PASSWORD);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> guestGet() {
        return new HttpEntity<>(new HttpHeaders());
    }

    private HttpEntity<Object> guestJsonRequest(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return (List<Map<String, Object>>) response.getBody().data().get("items");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> contentOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return (List<Map<String, Object>>) response.getBody().data().get("content");
    }

    @DisplayName("주문 생성 - POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("정상 요청이면, 201 Created와 함께 주문 정보가 반환되고 재고가 차감된다.")
        @Test
        void returnsCreated_andDecreasesStock() {
            // arrange
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            OrderV1Dto.CreateRequest requestBody =
                new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2)), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestBody),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> data = response.getBody().data();
            Map<String, Object> item = itemsOf(response).get(0);
            ProductModel reloadedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data).containsOnlyKeys("orderId", "status", "orderedAt", "originalAmount", "discountAmount", "finalAmount", "items"),
                () -> assertThat(data.get("status")).isEqualTo("CREATED"),
                () -> assertThat(((Number) data.get("originalAmount")).intValue()).isEqualTo(78_000),
                () -> assertThat(((Number) data.get("discountAmount")).intValue()).isZero(),
                () -> assertThat(((Number) data.get("finalAmount")).intValue()).isEqualTo(78_000),
                () -> assertThat(itemsOf(response)).hasSize(1),
                () -> assertThat(item).containsOnlyKeys("productId", "productName", "brandName", "unitPrice", "quantity"),
                () -> assertThat(((Number) item.get("productId")).longValue()).isEqualTo(product.getId()),
                () -> assertThat(item.get("productName")).isEqualTo("감성 가디건"),
                () -> assertThat(item.get("brandName")).isEqualTo("감성 브랜드"),
                () -> assertThat(((Number) item.get("unitPrice")).intValue()).isEqualTo(39_000),
                () -> assertThat(((Number) item.get("quantity")).intValue()).isEqualTo(2),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(48),
                () -> assertThat(user.getId()).isNotNull()
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            // arrange
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            OrderV1Dto.CreateRequest requestBody =
                new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2)), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                guestJsonRequest(requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getCode())
            );
        }

        @DisplayName("대상 상품이 존재하지 않으면, 404 Not Found로 거절되고 주문은 생성되지 않는다.")
        @Test
        void returnsNotFound_whenProductIsAbsent() {
            // arrange
            saveUser("kylekim");
            OrderV1Dto.CreateRequest requestBody =
                new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(99999L, 2)), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode()),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("수량이 0이면, 400 Bad Request로 거절되고 주문은 생성되지 않는다.")
        @Test
        void returnsBadRequest_whenQuantityIsZero() {
            // arrange
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            OrderV1Dto.CreateRequest requestBody =
                new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 0)), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("재고가 요청 수량에 미치지 못하면, 409 Conflict로 거절되고 재고는 변하지 않으며 주문은 생성되지 않는다.")
        @Test
        void returnsConflict_whenStockIsInsufficient() {
            // arrange
            saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 3);
            OrderV1Dto.CreateRequest requestBody =
                new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 5)), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestBody),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode()),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(3),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("여러 항목을 묶어 주문하면, 201 Created와 함께 각 항목 재고가 차감되고 총액이 합산된다.")
        @Test
        void returnsCreated_withMultipleItems() {
            // arrange
            saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel firstProduct = saveProduct(brand.getId(), "감성 가디건", 10_000, 50);
            ProductModel secondProduct = saveProduct(brand.getId(), "감성 머플러", 5_000, 50);
            OrderV1Dto.CreateRequest requestBody = new OrderV1Dto.CreateRequest(List.of(
                new OrderV1Dto.OrderItemRequest(firstProduct.getId(), 1),
                new OrderV1Dto.OrderItemRequest(secondProduct.getId(), 2)), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestBody),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedFirst = productJpaRepository.findById(firstProduct.getId()).orElseThrow();
            ProductModel reloadedSecond = productJpaRepository.findById(secondProduct.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(itemsOf(response)).hasSize(2),
                () -> assertThat(((Number) response.getBody().data().get("originalAmount")).intValue()).isEqualTo(20_000),
                () -> assertThat(reloadedFirst.getStock().value()).isEqualTo(49),
                () -> assertThat(reloadedSecond.getStock().value()).isEqualTo(48)
            );
        }

        @DisplayName("주문 항목이 비어 있으면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenItemsAreEmpty() {
            // arrange
            saveUser("kylekim");
            OrderV1Dto.CreateRequest requestBody = new OrderV1Dto.CreateRequest(List.of(), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("같은 상품이 둘 이상 포함되면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenProductIsDuplicated() {
            // arrange
            saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            OrderV1Dto.CreateRequest requestBody = new OrderV1Dto.CreateRequest(List.of(
                new OrderV1Dto.OrderItemRequest(product.getId(), 1),
                new OrderV1Dto.OrderItemRequest(product.getId(), 2)), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestBody),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode()),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("어느 한 항목의 상품이 없으면, 404 Not Found로 거절되고 다른 항목 재고는 원복된다.")
        @Test
        void returnsNotFound_whenAnyItemProductIsAbsent_andRollsBack() {
            // arrange
            saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            OrderV1Dto.CreateRequest requestBody = new OrderV1Dto.CreateRequest(List.of(
                new OrderV1Dto.OrderItemRequest(product.getId(), 1),
                new OrderV1Dto.OrderItemRequest(99999L, 1)), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestBody),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode()),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(50),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("어느 한 항목의 재고가 부족하면, 409 Conflict로 거절되고 다른 항목 재고는 원복된다.")
        @Test
        void returnsConflict_whenAnyItemStockIsInsufficient_andRollsBack() {
            // arrange
            saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel sufficientProduct = saveProduct(brand.getId(), "재고 충분 상품", 10_000, 10);
            ProductModel insufficientProduct = saveProduct(brand.getId(), "재고 부족 상품", 5_000, 2);
            OrderV1Dto.CreateRequest requestBody = new OrderV1Dto.CreateRequest(List.of(
                new OrderV1Dto.OrderItemRequest(sufficientProduct.getId(), 5),
                new OrderV1Dto.OrderItemRequest(insufficientProduct.getId(), 5)), null);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestBody),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedSufficient = productJpaRepository.findById(sufficientProduct.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode()),
                () -> assertThat(reloadedSufficient.getStock().value()).isEqualTo(10),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }
    }

    @DisplayName("쿠폰 적용 주문 생성 - POST /api/v1/orders (userCouponId)")
    @Nested
    class CreateOrderWithCoupon {

        private CouponModel saveCoupon(int discountValue, Integer minOrderAmount) {
            return couponJpaRepository.save(CouponModel.builder()
                .rawName("할인 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(discountValue)
                .rawMinOrderAmount(minOrderAmount)
                .rawExpiredAt(ZonedDateTime.now().plusDays(7))
                .now(ZonedDateTime.now())
                .build());
        }

        private UserCouponModel saveUserCoupon(Long userId, CouponModel coupon) {
            return userCouponJpaRepository.save(UserCouponModel.issue(userId, coupon));
        }

        private UserCouponModel saveUsedUserCoupon(Long userId, CouponModel coupon) {
            return userCouponJpaRepository.save(UserCouponModel.builder()
                .userId(userId)
                .couponId(coupon.getId())
                .name(coupon.getName().value())
                .discountType(coupon.getType())
                .discountValue(coupon.getDiscountValue())
                .minOrderAmount(coupon.getMinOrderAmount().value())
                .expiredAt(coupon.getExpiredAt().value())
                .usedAt(ZonedDateTime.now())
                .build());
        }

        private UserCouponModel saveExpiredUserCoupon(Long userId) {
            ZonedDateTime pastExpiredAt = ZonedDateTime.now().minusDays(1);
            CouponModel expiredCoupon = couponJpaRepository.save(CouponModel.builder()
                .rawName("만료 쿠폰")
                .type(DiscountType.FIXED)
                .rawValue(5_000)
                .rawMinOrderAmount(10_000)
                .rawExpiredAt(pastExpiredAt)
                .now(pastExpiredAt.minusDays(1))
                .build());

            return userCouponJpaRepository.save(UserCouponModel.issue(userId, expiredCoupon));
        }

        private OrderV1Dto.CreateRequest requestWithCoupon(Long productId, int quantity, Long userCouponId) {
            return new OrderV1Dto.CreateRequest(List.of(new OrderV1Dto.OrderItemRequest(productId, quantity)), userCouponId);
        }

        @DisplayName("사용 가능한 쿠폰을 적용하면, 201 Created와 함께 세 금액·적용 쿠폰 식별자가 반환되고 쿠폰이 사용 완료로 전이된다.")
        @Test
        void returnsCreated_withDiscount_andUsesCoupon() {
            // arrange (78,000원 주문 + 정액 5,000원 쿠폰)
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            UserCouponModel userCoupon = saveUserCoupon(user.getId(), saveCoupon(5_000, 10_000));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestWithCoupon(product.getId(), 2, userCoupon.getId())),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> data = response.getBody().data();
            UserCouponModel reloadedCoupon = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data)
                    .containsOnlyKeys("orderId", "status", "orderedAt", "originalAmount", "discountAmount", "finalAmount", "userCouponId", "items"),
                () -> assertThat(((Number) data.get("originalAmount")).intValue()).isEqualTo(78_000),
                () -> assertThat(((Number) data.get("discountAmount")).intValue()).isEqualTo(5_000),
                () -> assertThat(((Number) data.get("finalAmount")).intValue()).isEqualTo(73_000),
                () -> assertThat(((Number) data.get("userCouponId")).longValue()).isEqualTo(userCoupon.getId()),
                () -> assertThat(reloadedCoupon.getUsedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 지정하면, 404 Not Found로 거절되고 재고는 원복된다.")
        @Test
        void returnsNotFound_whenCouponIsAbsent_andRollsBack() {
            // arrange
            saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestWithCoupon(product.getId(), 2, 99999L)),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode()),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(50),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("타 회원 소유 쿠폰을 지정하면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenCouponBelongsToOther() {
            // arrange
            UserModel owner = saveUser("owner");
            saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            UserCouponModel othersCoupon = saveUserCoupon(owner.getId(), saveCoupon(5_000, 10_000));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestWithCoupon(product.getId(), 2, othersCoupon.getId())),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode()),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("이미 사용한 쿠폰을 지정하면, 409 Conflict로 거절된다.")
        @Test
        void returnsConflict_whenCouponIsAlreadyUsed() {
            // arrange
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            UserCouponModel usedCoupon = saveUsedUserCoupon(user.getId(), saveCoupon(5_000, 10_000));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestWithCoupon(product.getId(), 2, usedCoupon.getId())),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode()),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("만료된 쿠폰을 지정하면, 409 Conflict로 거절된다.")
        @Test
        void returnsConflict_whenCouponIsExpired() {
            // arrange
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            UserCouponModel expiredCoupon = saveExpiredUserCoupon(user.getId());

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestWithCoupon(product.getId(), 2, expiredCoupon.getId())),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode()),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("할인 전 주문 금액이 최소 주문 금액에 미치지 못하면, 409 Conflict로 거절되고 재고가 원복되며 쿠폰은 사용되지 않는다.")
        @Test
        void returnsConflict_whenOrderAmountIsBelowMinimum_andRollsBack() {
            // arrange (78,000원 주문 + 최소 100,000원 쿠폰)
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 50);
            UserCouponModel userCoupon = saveUserCoupon(user.getId(), saveCoupon(5_000, 100_000));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestWithCoupon(product.getId(), 2, userCoupon.getId())),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            UserCouponModel reloadedCoupon = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode()),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(50),
                () -> assertThat(reloadedCoupon.getUsedAt()).isNull(),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }

        @DisplayName("재고가 부족하면, 409 Conflict로 거절되고 쿠폰은 사용되지 않는다(전체 롤백).")
        @Test
        void returnsConflict_whenStockIsInsufficient_andCouponStaysUnused() {
            // arrange
            UserModel user = saveUser("kylekim");
            BrandModel brand = saveBrand("감성 브랜드");
            ProductModel product = saveProduct(brand.getId(), "감성 가디건", 39_000, 1);
            UserCouponModel userCoupon = saveUserCoupon(user.getId(), saveCoupon(5_000, 10_000));

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                memberJsonRequest("kylekim", requestWithCoupon(product.getId(), 5, userCoupon.getId())),
                MAP_RESPONSE
            );

            // assert
            ProductModel reloadedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            UserCouponModel reloadedCoupon = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode()),
                () -> assertThat(reloadedProduct.getStock().value()).isEqualTo(1),
                () -> assertThat(reloadedCoupon.getUsedAt()).isNull(),
                () -> assertThat(orderJpaRepository.findAll()).isEmpty()
            );
        }
    }

    @DisplayName("본인 주문 상세 - GET /api/v1/orders/{orderId}")
    @Nested
    class ReadMyOrder {

        @DisplayName("본인 주문이면, 200 OK와 함께 주문 항목 전체를 포함한 상세가 반환된다.")
        @Test
        void returnsOk_withOrderDetail() {
            // arrange
            UserModel user = saveUser("kylekim");
            OrderModel order = saveOrder(user.getId(), 10L);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + order.getId(),
                HttpMethod.GET,
                memberGet("kylekim"),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> data = response.getBody().data();
            Map<String, Object> item = itemsOf(response).get(0);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data).containsOnlyKeys("orderId", "status", "orderedAt", "originalAmount", "discountAmount", "finalAmount", "items"),
                () -> assertThat(((Number) data.get("orderId")).longValue()).isEqualTo(order.getId()),
                () -> assertThat(itemsOf(response)).hasSize(1),
                () -> assertThat(item).containsOnlyKeys("productId", "productName", "brandName", "unitPrice", "quantity")
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            // arrange
            UserModel user = saveUser("kylekim");
            OrderModel order = saveOrder(user.getId(), 10L);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + order.getId(),
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getCode())
            );
        }

        @DisplayName("존재하지 않는 주문이면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenOrderIsAbsent() {
            // arrange
            saveUser("kylekim");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/99999",
                HttpMethod.GET,
                memberGet("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }

        @DisplayName("타인의 주문이면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenOrderBelongsToOther() {
            // arrange
            UserModel owner = saveUser("owner");
            saveUser("kylekim");
            OrderModel order = saveOrder(owner.getId(), 10L);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + order.getId(),
                HttpMethod.GET,
                memberGet("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }
    }

    @DisplayName("본인 주문 내역 - GET /api/v1/orders")
    @Nested
    class ReadMyOrders {

        @DisplayName("기본 범위(시작일·종료일 미지정)로 요청하면, 200 OK와 함께 본인 주문과 페이지 메타가 반환된다.")
        @Test
        void returnsOk_withOrdersAndMeta() {
            // arrange
            UserModel user = saveUser("kylekim");
            saveOrder(user.getId(), 10L);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                memberGet("kylekim"),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> order = contentOf(response).get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> firstItem = ((List<Map<String, Object>>) order.get("items")).get(0);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data())
                    .containsKeys("content", "page", "size", "totalElements", "totalPages"),
                () -> assertThat(contentOf(response)).hasSize(1),
                () -> assertThat(order).containsOnlyKeys("orderId", "status", "orderedAt", "originalAmount", "discountAmount", "finalAmount", "items"),
                () -> assertThat(firstItem).containsOnlyKeys("productId", "productName", "brandName", "unitPrice", "quantity"),
                () -> assertThat(firstItem.get("productName")).isEqualTo("감성 가디건"),
                () -> assertThat(firstItem.get("brandName")).isEqualTo("감성 브랜드"),
                () -> assertThat(((Number) firstItem.get("unitPrice")).intValue()).isEqualTo(39_000),
                () -> assertThat(((Number) firstItem.get("quantity")).intValue()).isEqualTo(2)
            );
        }

        @DisplayName("범위 안에 주문이 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent() {
            // arrange
            UserModel user = saveUser("kylekim");
            saveOrder(user.getId(), 10L);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?startAt=2020-01-01&endAt=2020-12-31&page=0&size=20",
                HttpMethod.GET,
                memberGet("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).isEmpty()
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized로 거절된다.")
        @Test
        void returnsUnauthorized_whenAuthHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHENTICATED.getCode())
            );
        }

        @DisplayName("시작일이 종료일보다 늦으면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenStartIsAfterEnd() {
            // arrange
            saveUser("kylekim");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?startAt=2026-02-10&endAt=2026-01-01&page=0&size=20",
                HttpMethod.GET,
                memberGet("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("시작일이 오늘 이후이면, 400 Bad Request로 거절된다.")
        @Test
        void returnsBadRequest_whenStartIsAfterToday() {
            // arrange
            saveUser("kylekim");

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?startAt=2999-01-01&endAt=2999-12-31&page=0&size=20",
                HttpMethod.GET,
                memberGet("kylekim"),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }
    }
}
