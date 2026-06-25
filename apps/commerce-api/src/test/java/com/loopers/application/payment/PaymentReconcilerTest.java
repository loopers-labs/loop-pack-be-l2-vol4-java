package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.FakePaymentGateway;
import com.loopers.domain.payment.FakePaymentGatewayRouter;
import com.loopers.domain.payment.FakePaymentRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgProvider;
import com.loopers.domain.payment.PgResponse;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.payment.PgUnknownException;
import com.loopers.domain.payment.event.PaymentFailedEvent;
import com.loopers.domain.payment.event.PaymentSucceededEvent;
import com.loopers.domain.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PaymentReconciler 단위 테스트. 폴링과 타임아웃 처리의 핵심 분기를 검증한다.
 */
class PaymentReconcilerTest {

    private FakePaymentRepository paymentRepository;
    private ApplicationEventPublisher eventPublisher;
    private FakePaymentGateway gateway;
    private PaymentReconciler reconciler;

    @BeforeEach
    void setUp() {
        paymentRepository = new FakePaymentRepository();
        eventPublisher = mock(ApplicationEventPublisher.class);
        gateway = new FakePaymentGateway();
        FakePaymentGatewayRouter router = new FakePaymentGatewayRouter(gateway);
        PaymentService paymentService = new PaymentService(paymentRepository, eventPublisher);
        reconciler = new PaymentReconciler(paymentRepository, paymentService, router);
        ReflectionTestUtils.setField(reconciler, "initialDelay", Duration.ofSeconds(30));
        ReflectionTestUtils.setField(reconciler, "timeout", Duration.ofMinutes(15));
    }

    private Payment saveInProgress(String txKey, ZonedDateTime createdAt) {
        Payment payment = Payment.request(1L, 100L, PgProvider.PG_SIMULATOR, Money.of(5_000L), CardType.SAMSUNG, "1451");
        paymentRepository.save(payment);
        payment.markInProgress(txKey);
        ReflectionTestUtils.setField(payment, "createdAt", createdAt);
        return payment;
    }

    @DisplayName("30초 이상 된 IN_PROGRESS 가 PG 에서 SUCCESS 면 markSuccess + 이벤트 발행.")
    @Test
    void reconcile_pgReturnsSuccess_marksSuccess() {
        Payment payment = saveInProgress("TX-1", ZonedDateTime.now().minusMinutes(2));
        gateway.setGetStatusBehavior(key -> new PgResponse(key, PgStatus.SUCCESS, "정상 승인"));

        reconciler.reconcile();

        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.SUCCESS);
        verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));
    }

    @DisplayName("PG 에서 FAILED 응답이면 markFailed + 이벤트 발행 (재고 복구는 OrderEventHandler 가 처리).")
    @Test
    void reconcile_pgReturnsFailed_marksFailed() {
        Payment payment = saveInProgress("TX-1", ZonedDateTime.now().minusMinutes(2));
        gateway.setGetStatusBehavior(key -> new PgResponse(key, PgStatus.FAILED, "한도 초과"));

        reconciler.reconcile();

        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.FAILED);
        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @DisplayName("15분 초과 결제는 PG 조회 없이 강제 FAILED 로 전이된다 (타임아웃 안전망).")
    @Test
    void reconcile_timeoutExpired_forcesFailed() {
        Payment payment = saveInProgress("TX-1", ZonedDateTime.now().minusMinutes(20));
        // gateway behavior 는 호출되지 않아야 함 — 타임아웃이 우선

        reconciler.reconcile();

        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.FAILED);
        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @DisplayName("transactionKey 가 없는 UNKNOWN 은 폴링 대상이지만 PG 조회 못 하므로 스킵 (타임아웃까지 대기).")
    @Test
    void reconcile_unknownWithoutTransactionKey_skipsPgCall() {
        // PG 호출 자체가 실패한 케이스 — markUnknown 으로 들어와서 transactionKey 가 null
        Payment payment = Payment.request(1L, 100L, PgProvider.PG_SIMULATOR, Money.of(5_000L), CardType.SAMSUNG, "1451");
        paymentRepository.save(payment);
        payment.markUnknown("PG 호출 실패");
        ReflectionTestUtils.setField(payment, "createdAt", ZonedDateTime.now().minusMinutes(2));

        reconciler.reconcile();

        // 폴링이 스킵되었으니 여전히 UNKNOWN
        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.UNKNOWN);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @DisplayName("PG 통신 실패(PgUnknownException) 는 다음 폴링까지 대기 (상태 변경 없음).")
    @Test
    void reconcile_pgCommunicationFailure_keepsStateForNextPoll() {
        Payment payment = saveInProgress("TX-1", ZonedDateTime.now().minusMinutes(2));
        gateway.setGetStatusBehavior(key -> {
            throw new PgUnknownException("read timeout");
        });

        reconciler.reconcile();

        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.IN_PROGRESS); // 변화 없음
    }
}
