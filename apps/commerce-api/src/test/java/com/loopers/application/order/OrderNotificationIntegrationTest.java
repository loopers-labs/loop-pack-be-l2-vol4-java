package com.loopers.application.order;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderNotificationIntegrationTest {

    private final OrderFacade orderFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final CouponService couponService;
    private final DatabaseCleanUp databaseCleanUp;

    // 주문 완료가 '도메인 이벤트(OrderPlacedEvent)' 로 분리 발행되어 알림 핸들러가 수신하는지 검증하기 위해 spy.
    @MockitoSpyBean
    private OrderNotificationHandler orderNotificationHandler;

    private static final ZonedDateTime FAR_FUTURE = ZonedDateTime.parse("2099-12-31T23:59:59+09:00");

    private Long userId;
    private Long productAId;

    @Autowired
    OrderNotificationIntegrationTest(
        OrderFacade orderFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        CouponService couponService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.orderFacade = orderFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.couponService = couponService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productAId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 100_000L, 10, brandId).id();
        userId = userFacade.signUp(new UserCommand.SignUp(
            "user01",
            "Abcd1234!",
            "김철수",
            LocalDate.of(1999, 3, 22),
            "user@example.com"
        )).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문이 완료되면, OrderPlacedEvent 가 발행되어 알림 핸들러가 수신한다.")
    @Test
    void publishesOrderPlacedEvent_whenOrderIsPlaced() {
        // given
        OrderCommand.Place command = new OrderCommand.Place(List.of(
            new OrderCommand.Line(productAId, 2)
        ));

        // when
        OrderInfo result = orderFacade.placeOrder(userId, command);

        // then
        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderNotificationHandler).on(captor.capture());
        OrderPlacedEvent event = captor.getValue();
        assertAll(
            () -> assertThat(event.orderId()).isEqualTo(result.id()),
            () -> assertThat(event.userId()).isEqualTo(userId),
            () -> assertThat(event.finalAmount()).isEqualTo(200_000L)
        );
    }

    @DisplayName("쿠폰을 적용해 주문하면, OrderPlacedEvent.finalAmount 는 할인 후 '실결제액' 이다 (할인 전 totalAmount 가 아님).")
    @Test
    void publishesFinalAmount_notTotalAmount_whenCouponApplied() {
        // given : 20만원 주문에 10% 정률 쿠폰 → 할인 2만, 실결제 18만 (total=200,000 ≠ final=180,000 으로 갈라진다)
        Long couponId = issueCoupon(userId, CouponType.RATE, 10L, null);
        OrderCommand.Place command = new OrderCommand.Place(
            List.of(new OrderCommand.Line(productAId, 2)), couponId);

        // when
        orderFacade.placeOrder(userId, command);

        // then : 알림은 구매자가 '실제로 낸 금액' 을 보여줘야 한다 → finalAmount = 180,000
        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderNotificationHandler).on(captor.capture());
        assertThat(captor.getValue().finalAmount()).isEqualTo(180_000L);
    }

    private Long issueCoupon(Long ownerId, CouponType type, long value, Long minOrderAmount) {
        Long policyId = couponService.createPolicy("테스트 쿠폰", type, value, minOrderAmount, FAR_FUTURE).getId();
        return couponService.issue(ownerId, policyId).getId();
    }
}
