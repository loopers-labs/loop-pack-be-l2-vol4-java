package com.loopers.infrastructure.payment;

import com.loopers.application.order.OrderRepository;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentRepository;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayStatus;
import com.loopers.domain.payment.PaymentMethod;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@org.springframework.test.context.ContextConfiguration(initializers = com.loopers.testcontainers.RedisTestContainersConfig.class)
class PaymentFallbackSchedulerTest {

    @Autowired
    private PaymentFallbackScheduler scheduler;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @SpyBean
    private PaymentGateway paymentGateway;

    @SpyBean
    private com.loopers.application.payment.NotificationService notificationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("생성된 지 30분이 지난 READY 결제 건은 스케줄러에 의해 감지되어 상태 보정 처리가 수행된다. 이때 결제가 이미 APPROVED였다면 취소 처리를 쏘고 실패 처리한다.")
    @Transactional
    void run_WithOldReadyPayment_ShouldProcessCorrectionAndCancelPgPayment() {
        // given
        // 1. 주문 생성 및 저장
        var order = new OrderModel(1L, null, new BigDecimal("5000"), BigDecimal.ZERO, new BigDecimal("5000"));
        var savedOrder = orderRepository.save(order);

        // 2. 30분 전 결제 READY 건 생성 및 저장
        var oldPayment = new PaymentModel(savedOrder.getId(), PaymentMethod.CARD, new BigDecimal("5000"));
        oldPayment = paymentRepository.save(oldPayment);
        
        // JPQL로 oldPayment의 createdAt을 35분 전으로 변경
        entityManager.createQuery("UPDATE PaymentModel p SET p.createdAt = :time WHERE p.id = :id")
                .setParameter("time", java.time.ZonedDateTime.now().minusMinutes(35))
                .setParameter("id", oldPayment.getId())
                .executeUpdate();

        // 3. 최근에 생성된(즉시) 결제 READY 건 생성 및 저장
        var order2 = new OrderModel(1L, null, new BigDecimal("10000"), BigDecimal.ZERO, new BigDecimal("10000"));
        var savedOrder2 = orderRepository.save(order2);
        var recentPayment = new PaymentModel(savedOrder2.getId(), PaymentMethod.CARD, new BigDecimal("10000"));
        recentPayment = paymentRepository.save(recentPayment);

        entityManager.flush();
        entityManager.clear();

        // PG Mocking: 30분 전 주문은 APPROVED를 주도록 설정하여 결제 취소 처리가 되게 함
        Mockito.doReturn(new PaymentGateway.PaymentGatewayQueryResult(PaymentGatewayStatus.APPROVED, "tx-fallback-123", java.time.LocalDateTime.now()))
                .when(paymentGateway).queryPaymentStatus(savedOrder.getId());
        Mockito.doReturn(new PaymentGateway.PaymentGatewayQueryResult(PaymentGatewayStatus.FAILED, null, null))
                .when(paymentGateway).queryPaymentStatus(savedOrder2.getId());

        // when
        scheduler.run();

        // then
        // 30분 전 결제 건: FAILED로 변경됨
        var processedPayment = paymentRepository.findById(oldPayment.getId()).orElseThrow();
        assertThat(processedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        // PG 결제 취소 호출 검증 (APPROVED 상태였으므로 취소 API 연동)
        Mockito.verify(paymentGateway, Mockito.times(1))
                .cancelPayment(Mockito.eq("tx-fallback-123"), Mockito.eq(new BigDecimal("5000.00")));

        // 알림 서비스 미송신 검증 (Fallback 스케줄러 시 타임아웃 알림 제외)
        Mockito.verify(notificationService, Mockito.never())
                .sendPaymentTimeout(Mockito.anyLong(), Mockito.anyLong());

        // 환불 알림 서비스 송신 검증 (환불 완료 알림은 발송됨)
        Mockito.verify(notificationService, Mockito.times(1))
                .sendPaymentRefund(Mockito.eq(1L), Mockito.eq(oldPayment.getId()));

        // 최근 결제 건: 여전히 READY 상태 유지
        var skippedPayment = paymentRepository.findById(recentPayment.getId()).orElseThrow();
        assertThat(skippedPayment.getStatus()).isEqualTo(PaymentStatus.READY);
    }
}
