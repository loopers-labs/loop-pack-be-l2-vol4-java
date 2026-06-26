package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.payment.PaymentGatewayStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers = com.loopers.testcontainers.RedisTestContainersConfig.class)
@org.springframework.context.annotation.Import(com.loopers.config.RedisListenerConfig.class)
class PaymentFacadeTest {

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private com.loopers.application.order.OrderRepository orderRepository;

    @Autowired
    private RedisTemplate<String, String> defaultRedisTemplate;

    @SpyBean
    private PaymentGateway paymentGateway;

    @Autowired
    private com.loopers.application.coupon.CouponRepository couponRepository;

    @Autowired
    private com.loopers.application.product.ProductRepository productRepository;

    @Autowired
    private com.loopers.application.brand.BrandRepository brandRepository;

    @SpyBean
    private NotificationService notificationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        // Redis 정리
        var keys = defaultRedisTemplate.keys("payment_retry:*");
        if (keys != null && !keys.isEmpty()) {
            defaultRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("결제 요청 시 결제가 READY 상태로 저장되고 Redis에 TTL 10초인 retry 이력 키가 생성되며, PG사 통신에 성공하면 APPROVED로 변경된다.")
    void processPayment_Success_ShouldSaveApproved() {
        // given
        Long orderId = 1L;
        BigDecimal amount = new BigDecimal("5000");
        PaymentMethod method = PaymentMethod.CARD;

        // when
        Long paymentId = paymentFacade.processPayment(orderId, method, amount);

        // then
        PaymentModel payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getAmount()).isEqualByComparingTo(amount);

        // 성공 시 Redis retry 키는 삭제되어야 함
        String redisKey = "payment_retry:" + paymentId;
        Boolean hasKey = defaultRedisTemplate.hasKey(redisKey);
        assertThat(hasKey).isFalse();
    }

    @Test
    @DisplayName("PG API 호출 시 예외(Timeout 등)가 발생하면, 트랜잭션이 롤백되지 않고 결제가 READY 상태를 유지하며 Redis에 retry 키가 존재한다.")
    void processPayment_PgTimeout_ShouldKeepReadyStatus() {
        // given
        Long orderId = 2L;
        BigDecimal amount = new BigDecimal("10000");
        PaymentMethod method = PaymentMethod.CARD;

        // PG사 호출 시 강제로 Timeout 예외 발생시킴
        Mockito.doThrow(new CoreException(ErrorType.INTERNAL_ERROR, "PG Gateway Timeout"))
                .when(paymentGateway).requestPayment(Mockito.eq(orderId), Mockito.any(), Mockito.any());

        // when
        Long paymentId = paymentFacade.processPayment(orderId, method, amount);

        // then
        PaymentModel payment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);

        // Redis에 retry count=0 키가 유지되어야 함
        String redisKey = "payment_retry:" + paymentId;
        String countVal = defaultRedisTemplate.opsForValue().get(redisKey);
        assertThat(countVal).isEqualTo("0");

        // TTL이 10초 근처로 설정되어 있는지 검증 (보통 즉시 조회하므로 0보다 큼)
        Long ttl = defaultRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("retryOrCompensatePayment 호출 시 PG사 결과가 APPROVED이면 결제가 APPROVED로 변경되고 주문이 COMPLETED로 변경되며 Redis retry 키가 제거된다.")
    void retryOrCompensatePayment_PgApproved_ShouldApprovePaymentAndCompleteOrder() {
        // given
        // 1. 주문 생성 및 저장
        var order = new com.loopers.domain.order.OrderModel(1L, null, new BigDecimal("5000"), BigDecimal.ZERO, new BigDecimal("5000"));
        var savedOrder = orderRepository.save(order);

        // 2. 결제 READY 생성 및 저장
        var payment = new PaymentModel(savedOrder.getId(), PaymentMethod.CARD, new BigDecimal("5000"));
        var savedPayment = paymentRepository.save(payment);

        // 3. Redis에 retry 키 등록 (count=0)
        String redisKey = "payment_retry:" + savedPayment.getId();
        defaultRedisTemplate.opsForValue().set(redisKey, "0");

        // 4. Mocking: PG사 상태 확인 API가 APPROVED를 리턴하도록 설정
        Mockito.doReturn(new PaymentGateway.PaymentGatewayQueryResult(PaymentGatewayStatus.APPROVED, "tx-12345", LocalDateTime.now()))
                .when(paymentGateway).queryPaymentStatus(savedOrder.getId());

        // when
        paymentFacade.retryOrCompensatePayment(savedPayment.getId());

        // then
        // 결제 상태 APPROVED 확인
        var updatedPayment = paymentRepository.findById(savedPayment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);

        // 주문 상태 COMPLETED 확인
        var updatedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(com.loopers.domain.order.OrderStatus.COMPLETED);

        // Redis retry 키 삭제 확인
        assertThat(defaultRedisTemplate.hasKey(redisKey)).isFalse();
    }

    @Test
    @DisplayName("retryOrCompensatePayment 호출 시 PG사 결과가 PENDING이고 재시도 횟수가 남았으면(count=0) 결제는 READY를 유지하고 Redis 키가 count=1로 갱신 및 TTL이 재설정된다.")
    void retryOrCompensatePayment_PgPendingAndRetryLeft_ShouldIncrementCountAndExtendTtl() {
        // given
        var order = new com.loopers.domain.order.OrderModel(1L, null, new BigDecimal("5000"), BigDecimal.ZERO, new BigDecimal("5000"));
        var savedOrder = orderRepository.save(order);

        var payment = new PaymentModel(savedOrder.getId(), PaymentMethod.CARD, new BigDecimal("5000"));
        var savedPayment = paymentRepository.save(payment);

        String redisKey = "payment_retry:" + savedPayment.getId();
        defaultRedisTemplate.opsForValue().set(redisKey, "0"); // count=0

        Mockito.doReturn(new PaymentGateway.PaymentGatewayQueryResult(PaymentGatewayStatus.PENDING, null, null))
                .when(paymentGateway).queryPaymentStatus(savedOrder.getId());

        // when
        paymentFacade.retryOrCompensatePayment(savedPayment.getId());

        // then
        var updatedPayment = paymentRepository.findById(savedPayment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.READY);

        // Redis 키가 count=1로 증가
        String countVal = defaultRedisTemplate.opsForValue().get(redisKey);
        assertThat(countVal).isEqualTo("1");

        // TTL 재설정 확인
        Long ttl = defaultRedisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("retryOrCompensatePayment 호출 시 PG사 결과가 PENDING이고 재시도 횟수를 초과했으면(count=2) 결제 실패, 주문 취소, 재고 및 쿠폰 원복 처리가 수행되고 알림이 발송된다.")
    void retryOrCompensatePayment_PgPendingAndRetryExceeded_ShouldFailPaymentAndCancelOrderAndRestoreStockAndCoupon() {
        // given
        // 1. 쿠폰 템플릿 및 발급 정보 세팅
        var template = new com.loopers.domain.coupon.CouponTemplate(
                "테스트 쿠폰",
                com.loopers.domain.coupon.CouponType.FIXED,
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                null,
                LocalDateTime.now().plusDays(1)
        );
        var savedTemplate = couponRepository.saveTemplate(template);
        var couponIssue = new com.loopers.domain.coupon.CouponIssue(1L, savedTemplate);
        couponIssue.markUsed(); // 주문 시 선점되어 USED 상태인 것으로 간주
        var savedCouponIssue = couponRepository.saveIssue(couponIssue);

        // 2. 브랜드, 상품, 재고 세팅
        var brand = brandRepository.save(new com.loopers.domain.brand.BrandModel("Nike"));
        var product = new com.loopers.domain.product.ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000"));
        product.assignStock(10); // 초기 재고 10개
        var savedProduct = productRepository.save(product);
        
        // 주문 생성 시 1개 차감되었다고 가정하여, DB에는 9개로 선점되어 있는 상태
        savedProduct.getStock().decrease(1);
        productRepository.save(savedProduct);

        // 3. 주문 생성 및 저장 (쿠폰 and 아이템 포함)
        var order = new com.loopers.domain.order.OrderModel(1L, savedCouponIssue.getId(), new BigDecimal("200000"), new BigDecimal("1000"), new BigDecimal("199000"));
        var snapshot = new com.loopers.domain.order.ProductSnapshot(savedProduct.getName(), savedProduct.getPrice(), brand.getName());
        var orderItem = new com.loopers.domain.order.OrderItemModel(order, savedProduct.getId(), snapshot, 1);
        order.addItem(orderItem);
        var savedOrder = orderRepository.save(order);

        // 4. 결제 READY 생성 및 저장
        var payment = new PaymentModel(savedOrder.getId(), PaymentMethod.CARD, new BigDecimal("199000"));
        var savedPayment = paymentRepository.save(payment);

        // 5. Redis에 retry 키 등록 (count=2)
        String redisKey = "payment_retry:" + savedPayment.getId();
        defaultRedisTemplate.opsForValue().set(redisKey, "2");

        // Mocking: PG사 상태 확인 API가 PENDING을 리턴하도록 설정
        Mockito.doReturn(new PaymentGateway.PaymentGatewayQueryResult(PaymentGatewayStatus.PENDING, null, null))
                .when(paymentGateway).queryPaymentStatus(savedOrder.getId());

        // when
        paymentFacade.retryOrCompensatePayment(savedPayment.getId());

        // then
        // 결제 상태 FAILED 확인
        var updatedPayment = paymentRepository.findById(savedPayment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        // 주문 상태 CANCELED 확인
        var updatedOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(com.loopers.domain.order.OrderStatus.CANCELED);

        // 재고 원복 확인 (9개 -> 10개)
        var updatedProduct = productRepository.findById(savedProduct.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(10);

        // 쿠폰 원복 확인 (USED -> AVAILABLE)
        var updatedCoupon = couponRepository.findIssueById(savedCouponIssue.getId()).orElseThrow();
        assertThat(updatedCoupon.getStatus()).isEqualTo(com.loopers.domain.coupon.CouponStatus.AVAILABLE);

        // 알림 호출 확인
        Mockito.verify(notificationService, Mockito.times(1))
                .sendPaymentTimeout(Mockito.eq(1L), Mockito.eq(savedPayment.getId()));

        // Redis retry 키 삭제 확인
        assertThat(defaultRedisTemplate.hasKey(redisKey)).isFalse();
    }
}
