package com.loopers.application.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandRepository;
import com.loopers.domain.coupon.*;
import com.loopers.application.coupon.CouponRepository;
import com.loopers.application.order.OrderRepository;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.application.payment.PaymentRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers = com.loopers.testcontainers.RedisTestContainersConfig.class)
class OrderFacadeConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private com.loopers.application.payment.PaymentFacade paymentFacade;

    @SpyBean
    private PaymentGateway paymentGateway;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("재고가 5개 있는 상품에 대해 10명의 사용자가 동시에 checkout을 요청하면 5명만 성공하고 5명은 실패한다.")
    void checkout_ConcurrentStockDecrease_ShouldLimitToStockQuantity() throws InterruptedException {
        // given
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000"));
        product.assignStock(5);
        product = productRepository.save(product);
        Long productId = product.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        try {
            // when
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        barrier.await();
                        OrderCreateRequest request = new OrderCreateRequest(
                                List.of(new OrderCreateRequest.Item(productId, 1)),
                                null
                        );
                        orderFacade.createOrder(userId, request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            doneLatch.await();

            // then
            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failCount.get()).isEqualTo(5);

            // 남은 재고 확인
            ProductModel updatedProduct = productRepository.findById(productId).orElseThrow();
            assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(0);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("동일한 쿠폰을 사용하여 10개의 스레드에서 동시에 checkout을 요청하면 1개만 성공하고 나머지는 실패한다. (Double Spending 방어)")
    void checkout_ConcurrentCouponUse_ShouldPreventDoubleSpending() throws InterruptedException {
        // given
        Long userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        
        ProductModel product = new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000"));
        product.assignStock(50);
        product = productRepository.save(product);
        Long productId = product.getId();

        // 쿠폰 템플릿 및 발급 데이터 세팅
        CouponTemplate template = new CouponTemplate(
                "10% Discount",
                CouponType.RATE,
                new BigDecimal("10"),
                new BigDecimal("20000"),
                new BigDecimal("50000"),
                LocalDateTime.now().plusDays(7)
        );
        template = couponRepository.saveTemplate(template);

        CouponIssue couponIssue = new CouponIssue(userId, template);
        couponIssue = couponRepository.saveIssue(couponIssue);
        Long couponIssueId = couponIssue.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        try {
            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        barrier.await();
                        OrderCreateRequest request = new OrderCreateRequest(
                                List.of(new OrderCreateRequest.Item(productId, 1)),
                                couponIssueId
                        );
                        orderFacade.createOrder(userId, request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            doneLatch.await();

            // then
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(9);

            // 쿠폰 상태 USED 및 낙관적 락 버전 갱신 확인
            CouponIssue updatedIssue = couponRepository.findIssueById(couponIssueId).orElseThrow();
            assertThat(updatedIssue.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(updatedIssue.getVersion()).isGreaterThan(0L);
        } finally {
            executorService.shutdown();
        }
    }

    @Test
    @DisplayName("결제 진행 중 쿠폰 만료 시간이 지나도 정상적으로 결제가 완료되고 쿠폰이 사용 처리된다.")
    void checkout_CouponExpirationBoundary_ShouldNotThrowException() throws InterruptedException {
        // given
        Long userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        
        ProductModel product = new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000"));
        product.assignStock(10);
        product = productRepository.save(product);
        Long productId = product.getId();

        // 1초 뒤에 만료되는 쿠폰 생성
        CouponTemplate template = new CouponTemplate(
                "Boundary Coupon",
                CouponType.FIXED,
                new BigDecimal("10000"),
                new BigDecimal("0"),
                null,
                LocalDateTime.now().plusSeconds(1)
        );
        template = couponRepository.saveTemplate(template);

        CouponIssue couponIssue = new CouponIssue(userId, template);
        couponIssue = couponRepository.saveIssue(couponIssue);
        Long couponIssueId = couponIssue.getId();

        // 결제 승인에 1.5초가 걸린다고 가정
        Mockito.doAnswer(invocation -> {
            Thread.sleep(1500);
            return new com.loopers.domain.payment.PaymentGateway.PaymentGatewayResult("tx-boundary-123", LocalDateTime.now());
        }).when(paymentGateway).requestPayment(Mockito.anyLong(), Mockito.any(), Mockito.any());

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderCreateRequest.Item(productId, 1)),
                couponIssueId
        );

        // when
        Long orderId = orderFacade.createOrder(userId, request);
        Long paymentId = paymentFacade.processPayment(orderId, PaymentMethod.CARD, new BigDecimal("190000"));

        // then
        assertThat(orderId).isNotNull();
        com.loopers.domain.payment.PaymentStatus paymentStatus = paymentFacade.getPaymentStatus(paymentId);
        assertThat(paymentStatus).isEqualTo(com.loopers.domain.payment.PaymentStatus.APPROVED);
        CouponIssue updatedIssue = couponRepository.findIssueById(couponIssueId).orElseThrow();
        assertThat(updatedIssue.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @Autowired
    private IdempotencyManager idempotencyManager;

    @Test
    @DisplayName("멱등키를 사용한 첫 번째 요청이 진행 중일 때, 동일한 멱등키의 두 번째 요청은 Conflict 예외가 발생한다.")
    void checkout_Idempotency_ShouldPreventConcurrentRequests() throws Exception {
        // given
        Long userId = 1L;
        BrandModel brand = brandRepository.save(new BrandModel("Nike"));
        ProductModel product = new ProductModel(brand.getId(), "Air Jordan", new BigDecimal("200000"));
        product.assignStock(10);
        product = productRepository.save(product);
        Long productId = product.getId();

        String idempotencyKey = "idem-key-123";
        String namespacedKey = "order:create:" + userId + ":" + idempotencyKey;

        // 메인 스레드에서 먼저 락을 획득하여 첫 번째 요청이 길어지는 상황을 시뮬레이션
        boolean locked = idempotencyManager.lock(namespacedKey);
        assertThat(locked).isTrue();

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderCreateRequest.Item(productId, 1)),
                null
        );

        // when & then
        // 다른 스레드에서 같은 멱등키로 주문 생성을 시도하면 락을 획득하지 못하고 즉시 CONFLICT 발생
        ExecutorService executor = Executors.newSingleThreadExecutor();
        var future = executor.submit(() -> {
            return orderFacade.createOrder(userId, request, idempotencyKey);
        });

        try {
            future.get();
            org.junit.jupiter.api.Assertions.fail("예외가 발생해야 합니다.");
        } catch (java.util.concurrent.ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(CoreException.class);
            CoreException ce = (CoreException) e.getCause();
            assertThat(ce.getErrorType()).isEqualTo(com.loopers.support.error.ErrorType.CONFLICT);
        } finally {
            idempotencyManager.unlock(namespacedKey);
            executor.shutdown();
        }
    }
}
