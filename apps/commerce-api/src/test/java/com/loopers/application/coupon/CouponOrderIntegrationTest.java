package com.loopers.application.coupon;

import com.loopers.application.ordering.order.OrderCommand;
import com.loopers.application.ordering.order.OrderFacade;
import com.loopers.application.ordering.order.OrderResult;
import com.loopers.application.payment.payment.PaymentResultService;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.domain.ordering.order.OrderStatus;
import com.loopers.domain.payment.payment.PaymentRepository;
import com.loopers.domain.payment.payment.PaymentStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class CouponOrderIntegrationTest {

    private final CouponCommandService couponCommandService;
    private final CouponQueryService couponQueryService;
    private final OrderFacade orderFacade;
    private final PaymentResultService paymentResultService;
    private final PaymentRepository paymentRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    CouponOrderIntegrationTest(
        CouponCommandService couponCommandService,
        CouponQueryService couponQueryService,
        OrderFacade orderFacade,
        PaymentResultService paymentResultService,
        PaymentRepository paymentRepository,
        EventOutboxRepository eventOutboxRepository,
        BrandRepository brandRepository,
        ProductRepository productRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.couponCommandService = couponCommandService;
        this.couponQueryService = couponQueryService;
        this.orderFacade = orderFacade;
        this.paymentResultService = paymentResultService;
        this.paymentRepository = paymentRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("100% 할인 주문은 결제 row 없이 즉시 PAID가 되고 ORDER_PAID outbox를 저장한다.")
    @Test
    void completesZeroAmountOrderWithoutPayment() {
        Product product = saveProduct(2_000L, 1);
        Long issuedCouponId = issueCoupon(CouponType.RATE, 100L);

        OrderResult.Detail result = orderFacade.placeOrder(new OrderCommand.Create(
            "user1",
            List.of(new OrderCommand.Item(product.getId(), 1)),
            issuedCouponId
        ));

        assertAll(
            () -> assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAID),
            () -> assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.NOT_REQUIRED),
            () -> assertThat(result.originalAmount()).isEqualTo(2_000L),
            () -> assertThat(result.discountAmount()).isEqualTo(2_000L),
            () -> assertThat(result.finalAmount()).isZero(),
            () -> assertThat(paymentRepository.findByOrderId(result.orderId())).isEmpty(),
            () -> assertThat(eventOutboxRepository.findPendingEvents()).hasSize(1)
        );
    }

    @DisplayName("결제 실패 시 차감한 재고와 사용한 쿠폰을 함께 복구한다.")
    @Test
    void restoresStockAndCoupon_whenPaymentFails() {
        Product product = saveProduct(2_000L, 1);
        Long issuedCouponId = issueCoupon(CouponType.FIXED, 500L);
        OrderResult.Detail result = orderFacade.placeOrder(new OrderCommand.Create(
            "user1",
            List.of(new OrderCommand.Item(product.getId(), 1)),
            issuedCouponId
        ));

        paymentResultService.failPaymentAndRestoreStock(result.orderId(), "승인 실패");

        Product changedProduct = productRepository.find(product.getId()).orElseThrow();
        CouponResult.Issued issuedCoupon = couponQueryService.getMyCoupons("user1", 0, 20).items().get(0);
        assertAll(
            () -> assertThat(changedProduct.getStockQuantity()).isEqualTo(1),
            () -> assertThat(issuedCoupon.status()).isEqualTo(CouponStatus.AVAILABLE)
        );
    }

    private Long issueCoupon(CouponType type, Long value) {
        Long couponTemplateId = couponCommandService.createTemplate(new CouponCommand.CreateTemplate(
            "테스트 쿠폰",
            type,
            value,
            null,
            null,
            1,
            ZonedDateTime.now().plusDays(1)
        )).couponId();
        return couponCommandService.issue(couponTemplateId, "user1").couponId();
    }

    private Product saveProduct(Long price, Integer stockQuantity) {
        Brand brand = brandRepository.save(new Brand("Loopers", "테스트 브랜드"));
        return productRepository.save(new Product(brand.getId(), "상품", "설명", price, stockQuantity));
    }
}
