package com.loopers.order.interfaces.api;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandCommand;
import com.loopers.coupon.application.CouponAdminService;
import com.loopers.coupon.application.CouponCommand;
import com.loopers.coupon.application.CouponIssueService;
import com.loopers.coupon.application.CouponQueryService;
import com.loopers.coupon.domain.CouponStatus;
import com.loopers.coupon.domain.CouponType;
import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderRepository;
import com.loopers.order.domain.OrderStatus;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.product.application.ProductAdminService;
import com.loopers.product.application.ProductCommand;
import com.loopers.user.application.UserAccountService;
import com.loopers.user.application.UserCommand;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderCompensationE2ETest {

    private static final String LOGIN_ID = "loopers01";
    private static final String RAW_PASSWORD = "Passw0rd!";

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserAccountService userAccountService;
    @Autowired private BrandAdminService brandAdminService;
    @Autowired private ProductAdminService productAdminService;
    @Autowired private CouponAdminService couponAdminService;
    @Autowired private CouponIssueService couponIssueService;
    @Autowired private CouponQueryService couponQueryService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;
    private Long userCouponId;

    @BeforeEach
    void setUp() {
        doThrow(new RuntimeException("결제 실패")).when(paymentGateway).requestPayment(any(), any());

        userId = userAccountService.signUp(new UserCommand.SignUp(
                LOGIN_ID, RAW_PASSWORD, "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        )).id();
        Long brandId = brandAdminService.create(new BrandCommand.Create("루퍼스", "설명", null)).id();
        productId = productAdminService.create(new ProductCommand.Create(brandId, "셔츠", "설명", 29_000L, null, 50)).id();
        Long couponTemplateId = couponAdminService.create(
                new CouponCommand.Create("3천원 할인", CouponType.FIXED, 3_000L, null, ZonedDateTime.now().plusDays(30))
        ).id();
        userCouponId = couponIssueService.issue(userId, couponTemplateId).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("결제 실패 시 보상: 주문 FAILED, 재고 복구, 쿠폰 AVAILABLE 복원")
    void givenPaymentFails_whenPlaceOrderWithCoupon_thenCompensates() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        OrderV1Request.Create body = new OrderV1Request.Create(
                List.of(new OrderV1Request.Create.Line(productId, 2)),
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동",
                userCouponId
        );

        ParameterizedTypeReference<Object> type = new ParameterizedTypeReference<>() {};
        ResponseEntity<Object> response = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST, new HttpEntity<>(body, headers), type);

        Order order = orderRepository.findByUserId(userId).get(0);
        int stock = productAdminService.getProduct(productId).stockQuantity();
        CouponStatus couponStatus = couponQueryService.getMyCoupons(userId).coupons().get(0).status();

        assertAll(
                () -> assertThat(response.getStatusCode().is5xxServerError()).isTrue(),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED),
                () -> assertThat(stock).isEqualTo(50),
                () -> assertThat(couponStatus).isEqualTo(CouponStatus.AVAILABLE)
        );
    }
}
