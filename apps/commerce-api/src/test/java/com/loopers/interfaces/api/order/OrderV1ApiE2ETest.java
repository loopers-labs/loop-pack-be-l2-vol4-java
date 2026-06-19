package com.loopers.interfaces.api.order;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.coupon.IssuedCouponJpaEntity;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.brand.BrandJpaEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserDto;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT_ORDERS = "/api/v1/orders";
    private static final String ENDPOINT_SIGNUP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final CouponFacade couponFacade;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final IssuedCouponJpaRepository issuedCouponJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        CouponFacade couponFacade,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        IssuedCouponJpaRepository issuedCouponJpaRepository,
        OrderJpaRepository orderJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.couponFacade = couponFacade;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.issuedCouponJpaRepository = issuedCouponJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {
        @DisplayName("인증된 회원이 주문 가능한 상품을 주문하면, 주문을 생성하고 상품 재고를 차감한다.")
        @Test
        void createsOrderAndDeductsStock_whenProductsAreAvailable() {
            // arrange
            signup("user1234", "abc123!?");
            BrandJpaEntity brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductJpaEntity product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);
            OrderDto.Create.V1.Request request = new OrderDto.Create.V1.Request(
                List.of(new OrderDto.Create.V1.ProductRequest(product.getId(), 2))
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.Create.V1.Response>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    orderResponseType()
                );

            ProductJpaEntity savedProduct = productJpaRepository.findById(product.getId()).orElseThrow();

            // assert
            OrderDto.Create.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.userLoginId()).isEqualTo("user1234"),
                () -> assertThat(data.totalAmount()).isEqualTo(60_000L),
                () -> assertThat(data.originalAmount()).isEqualTo(60_000L),
                () -> assertThat(data.discountAmount()).isZero(),
                () -> assertThat(data.finalAmount()).isEqualTo(60_000L),
                () -> assertThat(data.orderLines()).hasSize(1),
                () -> assertThat(data.orderLines().get(0).productId()).isEqualTo(product.getId()),
                () -> assertThat(data.failures()).isEmpty(),
                () -> assertThat(savedProduct.getStock()).isEqualTo(8)
            );
        }

        @DisplayName("일부 상품의 재고가 부족하면, 전체 주문을 실패시키고 재고를 변경하지 않는다.")
        @Test
        void throwsConflictAndDoesNotDeductStock_whenSomeProductsAreOutOfStock() {
            // arrange
            signup("user1234", "abc123!?");
            BrandJpaEntity brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductJpaEntity availableProduct = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);
            ProductJpaEntity outOfStockProduct = saveProduct(brand.getId(), "셔츠", "가벼운 셔츠", 20_000L, 1);
            OrderDto.Create.V1.Request request = new OrderDto.Create.V1.Request(
                List.of(
                    new OrderDto.Create.V1.ProductRequest(availableProduct.getId(), 2),
                    new OrderDto.Create.V1.ProductRequest(outOfStockProduct.getId(), 3)
                )
            );

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            ProductJpaEntity savedAvailableProduct = productJpaRepository.findById(availableProduct.getId()).orElseThrow();
            ProductJpaEntity savedOutOfStockProduct = productJpaRepository.findById(outOfStockProduct.getId()).orElseThrow();

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(savedAvailableProduct.getStock()).isEqualTo(10),
                () -> assertThat(savedOutOfStockProduct.getStock()).isEqualTo(1)
            );
        }

        @DisplayName("쿠폰을 적용해 주문하면, 할인 금액을 반영하고 쿠폰을 사용 완료 처리한다.")
        @Test
        void createsOrderWithCouponAndMarksCouponUsed() {
            // arrange
            signup("user1234", "abc123!?");
            BrandJpaEntity brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductJpaEntity product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);
            ZonedDateTime now = ZonedDateTime.now();
            couponFacade.createCoupon(
                "사용하지 않는 1000원 할인",
                CouponType.FIXED,
                1_000L,
                0L,
                now.plusDays(7)
            );
            CouponInfo.Template orderCoupon = couponFacade.createCoupon(
                "신규가입 10% 할인",
                CouponType.RATE,
                10L,
                10_000L,
                now.plusDays(7)
            );
            CouponInfo.Issued issuedCoupon = couponFacade.issueCoupon(orderCoupon.id(), "user1234", now);
            OrderDto.Create.V1.Request request = new OrderDto.Create.V1.Request(
                List.of(new OrderDto.Create.V1.ProductRequest(product.getId(), 2)),
                orderCoupon.id()
            );

            // act
            ResponseEntity<ApiResponse<OrderDto.Create.V1.Response>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    orderResponseType()
                );

            ProductJpaEntity savedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            IssuedCouponJpaEntity savedIssuedCoupon = issuedCouponJpaRepository
                .findByCouponIdAndUserLoginIdAndDeletedAtIsNull(orderCoupon.id(), "user1234")
                .orElseThrow();

            // assert
            OrderDto.Create.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.originalAmount()).isEqualTo(60_000L),
                () -> assertThat(data.discountAmount()).isEqualTo(6_000L),
                () -> assertThat(data.finalAmount()).isEqualTo(54_000L),
                () -> assertThat(data.totalAmount()).isEqualTo(54_000L),
                () -> assertThat(orderCoupon.id()).isNotEqualTo(issuedCoupon.id()),
                () -> assertThat(savedProduct.getStock()).isEqualTo(8),
                () -> assertThat(savedIssuedCoupon.getStatus()).isEqualTo(CouponStatus.USED)
            );
        }

        @DisplayName("쿠폰을 사용할 수 없으면, 주문을 실패시키고 재고와 주문을 변경하지 않는다.")
        @Test
        void rollsBackStockAndOrder_whenCouponCannotBeUsed() {
            // arrange
            signup("user1234", "abc123!?");
            BrandJpaEntity brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductJpaEntity product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 10);
            OrderDto.Create.V1.Request request = new OrderDto.Create.V1.Request(
                List.of(new OrderDto.Create.V1.ProductRequest(product.getId(), 2)),
                9_999L
            );

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            ProductJpaEntity savedProduct = productJpaRepository.findById(product.getId()).orElseThrow();

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(savedProduct.getStock()).isEqualTo(10),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("주문 가능한 상품이 하나도 없으면, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenNoProductCanBeOrdered() {
            // arrange
            signup("user1234", "abc123!?");
            BrandJpaEntity brand = saveBrand("Loopers", "감성 이커머스 브랜드");
            ProductJpaEntity product = saveProduct(brand.getId(), "니트", "부드러운 니트", 30_000L, 1);
            OrderDto.Create.V1.Request request = new OrderDto.Create.V1.Request(
                List.of(new OrderDto.Create.V1.ProductRequest(product.getId(), 2))
            );

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenCredentialHeaderIsMissing() {
            // arrange
            OrderDto.Create.V1.Request request = new OrderDto.Create.V1.Request(
                List.of(new OrderDto.Create.V1.ProductRequest(1L, 1))
            );

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("주문 상품 목록이 비어있으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenProductsAreEmpty() {
            // arrange
            signup("user1234", "abc123!?");
            OrderDto.Create.V1.Request request = new OrderDto.Create.V1.Request(List.of());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_ORDERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    voidResponseType()
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private BrandJpaEntity saveBrand(String name, String description) {
        return brandJpaRepository.save(BrandJpaEntity.from(new Brand(name, description)));
    }

    private ProductJpaEntity saveProduct(Long brandId, String name, String description, Long price, Integer stock) {
        return productJpaRepository.save(ProductJpaEntity.from(new Product(brandId, name, description, price, stock)));
    }

    private void signup(String loginId, String password) {
        UserDto.Register.V1.Request request = new UserDto.Register.V1.Request(
            loginId,
            password,
            "홍길동",
            LocalDate.of(1990, 1, 15),
            loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, String.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private ParameterizedTypeReference<ApiResponse<OrderDto.Create.V1.Response>> orderResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private ParameterizedTypeReference<ApiResponse<Void>> voidResponseType() {
        return new ParameterizedTypeReference<>() {};
    }
}
