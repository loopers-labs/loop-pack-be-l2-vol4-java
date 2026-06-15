package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountPolicy;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.BirthDate;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.EncodedPassword;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
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

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String CUSTOMER = "/api/v1/orders";
    private static final String ADMIN = "/api-admin/v1/orders";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final InventoryJpaRepository inventoryJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productAId;
    private Long productBId;
    private Long user1Id;

    @Autowired
    public OrderV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            InventoryJpaRepository inventoryJpaRepository,
            UserJpaRepository userJpaRepository,
            CouponTemplateJpaRepository couponTemplateJpaRepository,
            UserCouponJpaRepository userCouponJpaRepository,
            PasswordEncoder passwordEncoder,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.inventoryJpaRepository = inventoryJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.couponTemplateJpaRepository = couponTemplateJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    private Long issueCoupon(Long userId, DiscountType type, long value) {
        CouponTemplate template = couponTemplateJpaRepository.save(
                CouponTemplate.create("쿠폰", DiscountPolicy.of(type, value), 30));
        return userCouponJpaRepository.save(UserCoupon.issue(userId, template, ZonedDateTime.now())).getId();
    }

    @BeforeEach
    void setUp() {
        Brand brand = brandJpaRepository.save(Brand.create("브랜드A", "소개"));
        productAId = productJpaRepository.save(Product.create(brand.getId(), "상품A", Money.of(1_000L))).getId();
        productBId = productJpaRepository.save(Product.create(brand.getId(), "상품B", Money.of(2_000L))).getId();
        inventoryJpaRepository.save(Inventory.create(productAId, 10));
        inventoryJpaRepository.save(Inventory.create(productBId, 5));
        user1Id = createUser("user1");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private static final String PASSWORD = "Abcd123!";

    private Long createUser(String loginId) {
        UserModel user = new UserModel(
                new LoginId(loginId),
                new Name("유저"),
                new BirthDate(LocalDate.of(1999, 1, 1)),
                new Email(loginId + "@loopers.com"),
                EncodedPassword.create(passwordEncoder, PASSWORD)
        );
        return userJpaRepository.save(user).getId();
    }

    private static HttpHeaders loginHeader(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        if (loginId != null) {
            headers.add("X-Loopers-LoginId", loginId);
            headers.add("X-Loopers-LoginPw", PASSWORD);
        }
        return headers;
    }

    private static HttpHeaders adminHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> place(String loginId, OrderV1Dto.PlaceRequest request) {
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreatedResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(CUSTOMER, HttpMethod.POST, new HttpEntity<>(request, loginHeader(loginId)), type);
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.DetailResponse>> getMyOrder(String loginId, Long orderId) {
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.DetailResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(CUSTOMER + "/" + orderId, HttpMethod.GET, new HttpEntity<>(loginHeader(loginId)), type);
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.PageResponse>> getMyOrders(String loginId) {
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.PageResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(CUSTOMER, HttpMethod.GET, new HttpEntity<>(loginHeader(loginId)), type);
    }

    private static OrderV1Dto.PlaceRequest oneLine(Long productId, int quantity) {
        return new OrderV1Dto.PlaceRequest(null, List.of(new OrderV1Dto.LineRequest(productId, quantity)));
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class Place {

        @DisplayName("정상 주문이면 200 과 생성된 주문을 반환하고 재고가 차감된다.")
        @Test
        void returnsOkAndDecreasesStock_whenValid() {
            OrderV1Dto.PlaceRequest request = new OrderV1Dto.PlaceRequest(null, List.of(
                    new OrderV1Dto.LineRequest(productAId, 2),
                    new OrderV1Dto.LineRequest(productBId, 1)
            ));

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = place("user1", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            OrderV1Dto.CreatedResponse body = response.getBody().data();
            assertThat(body.id()).isNotNull();
            assertThat(body.userId()).isEqualTo(user1Id);
            assertThat(body.totalAmount()).isEqualTo(4_000L);
            assertThat(body.items()).hasSize(2);

            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productAId).orElseThrow().getQuantity()).isEqualTo(8);
            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productBId).orElseThrow().getQuantity()).isEqualTo(4);
        }

        @DisplayName("쿠폰을 지정하면 200 과 함께 할인 적용된 금액 3종을 반환하고 쿠폰이 사용 완료된다.")
        @Test
        void returnsOkWithDiscount_whenCouponApplied() {
            Long couponId = issueCoupon(user1Id, DiscountType.FIXED, 500L);
            OrderV1Dto.PlaceRequest request = new OrderV1Dto.PlaceRequest(
                    couponId, List.of(new OrderV1Dto.LineRequest(productAId, 2))); // 2_000

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = place("user1", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            OrderV1Dto.CreatedResponse body = response.getBody().data();
            assertThat(body.originalAmount()).isEqualTo(2_000L);
            assertThat(body.discountAmount()).isEqualTo(500L);
            assertThat(body.totalAmount()).isEqualTo(1_500L);

            assertThat(userCouponJpaRepository.findById(couponId).orElseThrow().getStatus())
                    .isEqualTo(CouponStatus.USED);
        }

        @DisplayName("본인 소유가 아닌 쿠폰을 지정하면 403 을 반환하고 재고는 유지된다.")
        @Test
        void returnsForbidden_whenCouponNotOwned() {
            Long otherUserId = createUser("user2");
            Long couponId = issueCoupon(otherUserId, DiscountType.FIXED, 500L);
            OrderV1Dto.PlaceRequest request = new OrderV1Dto.PlaceRequest(
                    couponId, List.of(new OrderV1Dto.LineRequest(productAId, 2)));

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = place("user1", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productAId).orElseThrow().getQuantity()).isEqualTo(10);
        }

        @DisplayName("재고가 부족하면 400 을 반환하고 재고는 그대로 유지된다.")
        @Test
        void returnsBadRequest_andKeepsStock_whenShortage() {
            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = place("user1", oneLine(productAId, 100));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productAId).orElseThrow().getQuantity()).isEqualTo(10);
        }

        @DisplayName("존재하지 않는 상품을 주문하면 404 를 반환한다.")
        @Test
        void returnsNotFound_whenProductMissing() {
            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = place("user1", oneLine(99999L, 1));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("로그인 헤더가 누락되면 400 BAD_REQUEST.")
        @Test
        void returnsBadRequest_whenHeaderMissing() {
            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = place(null, oneLine(productAId, 1));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetMyOrder {

        @DisplayName("본인의 주문이면 200 과 상세를 반환한다.")
        @Test
        void returnsOk_whenOwner() {
            Long orderId = place("user1", oneLine(productAId, 1)).getBody().data().id();

            ResponseEntity<ApiResponse<OrderV1Dto.DetailResponse>> response = getMyOrder("user1", orderId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(orderId);
            assertThat(response.getBody().data().userId()).isEqualTo(user1Id);
        }

        @DisplayName("다른 사용자의 주문을 조회하면 403 을 반환한다.")
        @Test
        void returnsForbidden_whenNotOwner() {
            createUser("user2");
            Long orderId = place("user1", oneLine(productAId, 1)).getBody().data().id();

            ResponseEntity<ApiResponse<OrderV1Dto.DetailResponse>> response = getMyOrder("user2", orderId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("GET /api/v1/orders 는 본인 주문만 반환한다.")
    @Test
    void getMyOrders_returnsOnlyOwnersOrders() {
        createUser("user2");
        place("user1", oneLine(productAId, 1));
        place("user1", oneLine(productBId, 1));
        place("user2", oneLine(productAId, 1));

        ResponseEntity<ApiResponse<OrderV1Dto.PageResponse>> response = getMyOrders("user1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).hasSize(2);
        assertThat(response.getBody().data().content()).allMatch(o -> o.userId().equals(user1Id));
    }

    @DisplayName("어드민 GET /api-admin/v1/orders/{orderId} 는 본인 여부와 무관하게 단건을 반환한다.")
    @Test
    void getAdminOrder_returnsAnyUsersOrder() {
        Long orderId = place("user1", oneLine(productAId, 1)).getBody().data().id();

        ParameterizedTypeReference<ApiResponse<OrderV1Dto.DetailResponse>> type = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<OrderV1Dto.DetailResponse>> response = testRestTemplate.exchange(
                ADMIN + "/" + orderId, HttpMethod.GET, new HttpEntity<>(adminHeader()), type);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().userId()).isEqualTo(user1Id);
    }

    @DisplayName("어드민 GET /api-admin/v1/orders 는 모든 사용자의 주문을 반환한다.")
    @Test
    void getAdminOrders_returnsAllUsersOrders() {
        Long user2Id = createUser("user2");
        place("user1", oneLine(productAId, 1));
        place("user2", oneLine(productBId, 1));

        ParameterizedTypeReference<ApiResponse<OrderV1Dto.PageResponse>> type = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<OrderV1Dto.PageResponse>> response = testRestTemplate.exchange(
                ADMIN, HttpMethod.GET, new HttpEntity<>(adminHeader()), type);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).hasSize(2);
        assertThat(response.getBody().data().content()).extracting(OrderV1Dto.ListItemResponse::userId)
                .containsExactlyInAnyOrder(user1Id, user2Id);
    }
}
