package com.loopers.application.payment;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderLineCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.Discount;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.money.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class PaymentFacadeIntegrationTest {

    private static final LocalDateTime VALID_EXPIRED_AT = LocalDateTime.of(2099, 12, 31, 23, 59, 59);
    private static final String TX = "tx-001";
    private static final String CARD_NO = "1234-1234-1234-1234";

    @Autowired
    private OrderFacade orderFacade;
    @Autowired
    private PaymentFacade paymentFacade;
    @Autowired
    private BrandJpaRepository brandJpaRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private CouponJpaRepository couponJpaRepository;
    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;
    @Autowired
    private OrderJpaRepository orderJpaRepository;
    @Autowired
    private PaymentJpaRepository paymentJpaRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    // 주문 시 PG 호출은 막고, 결제는 transactionKey=TX 의 PENDING 으로 생성되게 한다.
    @MockitoBean
    private PaymentGateway paymentGateway;

    @BeforeEach
    void stubPaymentGateway() {
        given(paymentGateway.requestPayment(any()))
            .willReturn(new PaymentGateway.PaymentResult(TX, PaymentStatus.PENDING, null));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Product saveProduct(int stock) {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        return productJpaRepository.save(new Product("에어맥스", "편한 러닝화",
            new Money(BigDecimal.valueOf(1000)), new Stock(stock), brand.getId()));
    }

    private UserCoupon issueFixedCoupon(Long userId, long discountValue) {
        Coupon coupon = couponJpaRepository.save(new Coupon("정액 할인",
            new Discount(CouponType.FIXED, discountValue), null, VALID_EXPIRED_AT));
        return userCouponJpaRepository.save(new UserCoupon(userId, coupon.getId()));
    }

    private OrderInfo placeAndPay(Long userId, Long productId, int quantity, Long couponId) {
        OrderInfo info = orderFacade.place(userId,
            List.of(new OrderLineCommand(productId, quantity)), couponId);
        paymentFacade.pay(userId, info.id(), CardType.SAMSUNG, CARD_NO);
        return info;
    }

    @DisplayName("결제 성공 콜백이 오면, ")
    @Nested
    class OnSuccess {
        @DisplayName("주문은 PAID·결제는 SUCCESS 가 되고, 재고는 복원되지 않는다.")
        @Test
        void marksPaid() {
            // arrange
            Long userId = 1L;
            Product product = saveProduct(10);
            OrderInfo info = placeAndPay(userId, product.getId(), 3, null);

            // act
            paymentFacade.confirm(TX, PaymentStatus.SUCCESS, null);

            // assert
            Order order = orderJpaRepository.findById(info.id()).orElseThrow();
            Payment payment = paymentJpaRepository.findByTransactionKey(TX).orElseThrow();
            Product reloaded = productJpaRepository.findById(product.getId()).orElseThrow();
            assertAll(
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID),
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(reloaded.getStock().getQuantity()).isEqualTo(7)
            );
        }
    }

    @DisplayName("결제 실패 콜백이 오면, ")
    @Nested
    class OnFailure {
        @DisplayName("주문은 FAILED 가 되고, 재고와 쿠폰이 보상(원복)된다.")
        @Test
        void marksFailedAndCompensates() {
            // arrange
            Long userId = 1L;
            Product product = saveProduct(10);
            UserCoupon userCoupon = issueFixedCoupon(userId, 1000L);
            OrderInfo info = placeAndPay(userId, product.getId(), 3, userCoupon.getId());

            // act
            paymentFacade.confirm(TX, PaymentStatus.FAILED, "카드 한도 초과");

            // assert
            Order order = orderJpaRepository.findById(info.id()).orElseThrow();
            Product reloaded = productJpaRepository.findById(product.getId()).orElseThrow();
            UserCoupon reloadedCoupon = userCouponJpaRepository.findById(userCoupon.getId()).orElseThrow();
            assertAll(
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED),
                () -> assertThat(reloaded.getStock().getQuantity()).isEqualTo(10),
                () -> assertThat(reloadedCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE)
            );
        }
    }

    @DisplayName("같은 실패 콜백이 두 번 와도, ")
    @Nested
    class Idempotency {
        @DisplayName("두 번째는 멱등 무시되어 재고가 이중 복원되지 않는다.")
        @Test
        void ignoresDuplicateCallback() {
            // arrange
            Long userId = 1L;
            Product product = saveProduct(10);
            placeAndPay(userId, product.getId(), 3, null);

            // act
            paymentFacade.confirm(TX, PaymentStatus.FAILED, "실패");
            paymentFacade.confirm(TX, PaymentStatus.FAILED, "실패");

            // assert
            Product reloaded = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(reloaded.getStock().getQuantity()).isEqualTo(10);
        }
    }
}
