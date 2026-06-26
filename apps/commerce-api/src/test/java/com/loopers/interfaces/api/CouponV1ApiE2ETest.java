package com.loopers.interfaces.api;

import com.loopers.domain.order.PaymentMethod;
import com.loopers.interfaces.api.admin.AdminCouponV1Dto;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.coupon.CouponV1Dto;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @Autowired
    public CouponV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        signUp("buyer", "testPw1234", "구매자");
        Long brandId = createBrand("나이키", "스포츠");
        productId = createProduct(brandId, "에어맥스", 10000L, 10);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", "testPw1234");
        return headers;
    }

    private void signUp(String loginId, String password, String name) {
        testRestTemplate.postForEntity("/api/v1/users",
                new UserV1Dto.SignUpRequest(loginId, password, name, LocalDate.of(1992, 6, 24), "test@example.com"),
                Object.class);
    }

    private Long createBrand(String name, String description) {
        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                "/api/v1/brands", HttpMethod.POST,
                new HttpEntity<>(new BrandV1Dto.CreateBrandRequest(name, description)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private Long createProduct(Long brandId, String name, Long price, Integer stock) {
        ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                "/api/v1/products", HttpMethod.POST,
                new HttpEntity<>(new ProductV1Dto.CreateProductRequest(brandId, name, "설명", null, price, stock)),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    private Long createCouponTemplate(String type, long value, Long minOrderAmount) {
        ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> response = testRestTemplate.exchange(
                "/api-admin/v1/coupons", HttpMethod.POST,
                new HttpEntity<>(new AdminCouponV1Dto.CreateCouponRequest("할인", type, value, minOrderAmount,
                        LocalDateTime.now().plusDays(7))),
                new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    @DisplayName("쿠폰 발급 → 내 쿠폰 목록 → 쿠폰 적용 주문까지 동작한다")
    @Test
    void couponLifecycleE2E() {
        // 1. Admin이 정률 10% 템플릿 등록
        Long couponId = createCouponTemplate("RATE", 10L, 5000L);

        // 2. 사용자가 발급
        ResponseEntity<ApiResponse<CouponV1Dto.IssuedCouponResponse>> issued = testRestTemplate.exchange(
                "/api/v1/coupons/" + couponId + "/issue", HttpMethod.POST,
                new HttpEntity<>(authHeaders("buyer")),
                new ParameterizedTypeReference<>() {});
        assertAll(
                () -> assertThat(issued.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(issued.getBody().data().status()).isEqualTo("AVAILABLE"),
                () -> assertThat(issued.getBody().data().couponId()).isEqualTo(couponId)
        );

        // 3. 내 쿠폰 목록에 노출
        ResponseEntity<ApiResponse<List<CouponV1Dto.IssuedCouponResponse>>> myCoupons = testRestTemplate.exchange(
                "/api/v1/users/me/coupons", HttpMethod.GET,
                new HttpEntity<>(authHeaders("buyer")),
                new ParameterizedTypeReference<>() {});
        assertThat(myCoupons.getBody().data()).hasSize(1);

        // 4. 쿠폰을 적용해 주문 (10000 × 2 = 20000, 10% 할인 → 최종 18000). 결제는 분리돼 주문은 PENDING (03 §3.7)
        OrderV1Dto.PlaceOrderRequest orderRequest = new OrderV1Dto.PlaceOrderRequest(
                PaymentMethod.CARD, List.of(new OrderV1Dto.OrderLineRequest(productId, 2)), couponId);
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> order = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(orderRequest, authHeaders("buyer")),
                new ParameterizedTypeReference<>() {});
        assertAll(
                () -> assertThat(order.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(order.getBody().data().status()).isEqualTo("PENDING"),
                () -> assertThat(order.getBody().data().totalAmount()).isEqualTo(20000L),
                () -> assertThat(order.getBody().data().discountAmount()).isEqualTo(2000L),
                () -> assertThat(order.getBody().data().finalAmount()).isEqualTo(18000L)
        );

        // 5. 사용된 쿠폰은 USED로 바뀌어 재사용 불가 — 같은 쿠폰으로 다시 주문하면 실패(4xx)
        ResponseEntity<Object> reuse = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(orderRequest, authHeaders("buyer")),
                Object.class);
        assertThat(reuse.getStatusCode().is4xxClientError()).isTrue();
    }
}
