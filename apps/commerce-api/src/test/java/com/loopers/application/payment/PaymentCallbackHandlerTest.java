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
import com.loopers.interfaces.api.payment.PaymentCallbackV1Dto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PaymentCallbackHandler 단위 테스트. PG 가 보내는 콜백을 받아 상태 전이를 위임하는 흐름의 멱등성과 안전성을 검증.
 */
class PaymentCallbackHandlerTest {

    private static final String TX_KEY = "TX-1";

    private PaymentRepository paymentRepository;
    private ApplicationEventPublisher eventPublisher;
    private FakePaymentGateway gateway;
    private PaymentCallbackHandler handler;

    @BeforeEach
    void setUp() {
        paymentRepository = new FakePaymentRepository();
        eventPublisher = mock(ApplicationEventPublisher.class);
        gateway = new FakePaymentGateway();
        FakePaymentGatewayRouter router = new FakePaymentGatewayRouter(gateway);
        PaymentService paymentService = new PaymentService(paymentRepository, eventPublisher);
        handler = new PaymentCallbackHandler(paymentRepository, paymentService, router);
        // 기본 PG 재확인 동작 — 콜백과 동일한 결과를 PG 가 응답한다고 가정
        gateway.setGetStatusBehavior(key -> new PgResponse(key, PgStatus.SUCCESS, "정상"));
    }

    private Payment savedInProgress(String txKey) {
        Payment payment = Payment.request(1L, 100L, PgProvider.PG_SIMULATOR, Money.of(5_000L), CardType.SAMSUNG, "1451");
        paymentRepository.save(payment);
        payment.markInProgress(txKey);
        return payment;
    }

    private PaymentCallbackV1Dto callback(String txKey, String status, String reason) {
        return new PaymentCallbackV1Dto(txKey, "order-1", "SAMSUNG", "1234-5678-9814-1451", 5_000L, status, reason);
    }

    @DisplayName("SUCCESS 콜백을 받으면 Payment 가 SUCCESS 로 전이되고 이벤트가 발행된다.")
    @Test
    void handle_successCallback_marksSuccess() {
        Payment payment = savedInProgress(TX_KEY);

        handler.handle(callback(TX_KEY, "SUCCESS", "정상"));

        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.SUCCESS);
        verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));
    }

    @DisplayName("FAILED 콜백을 받으면 (PG 재확인도 FAILED) Payment 가 FAILED 로 전이되고 이벤트가 발행된다.")
    @Test
    void handle_failedCallback_marksFailed() {
        Payment payment = savedInProgress(TX_KEY);
        gateway.setGetStatusBehavior(key -> new PgResponse(key, PgStatus.FAILED, "한도초과입니다."));

        handler.handle(callback(TX_KEY, "FAILED", "한도초과입니다."));

        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.FAILED);
        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @DisplayName("콜백은 SUCCESS 이지만 PG 재확인이 FAILED 면 PG 결과를 신뢰해 FAILED 로 처리한다 (콜백 위조 방어).")
    @Test
    void handle_pgReverify_overridesCallback() {
        Payment payment = savedInProgress(TX_KEY);
        // 콜백 body: SUCCESS, 그러나 PG 실제 상태: FAILED
        gateway.setGetStatusBehavior(key -> new PgResponse(key, PgStatus.FAILED, "한도초과입니다."));

        handler.handle(callback(TX_KEY, "SUCCESS", "정상"));

        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.FAILED);
        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @DisplayName("PG 재확인 호출이 실패하면 콜백 body 로 fallback 해 처리한다 (안전망 우선).")
    @Test
    void handle_pgReverifyFails_fallsBackToCallbackBody() {
        Payment payment = savedInProgress(TX_KEY);
        gateway.setGetStatusBehavior(key -> {
            throw new PgUnknownException("PG 통신 실패");
        });

        handler.handle(callback(TX_KEY, "SUCCESS", "정상"));

        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.SUCCESS);
        verify(eventPublisher).publishEvent(any(PaymentSucceededEvent.class));
    }

    @DisplayName("매칭되는 Payment 가 없으면 무시한다 (응답<콜백 race / 모르는 transactionKey).")
    @Test
    void handle_unknownTransactionKey_ignoresSilently() {
        handler.handle(callback("UNKNOWN-KEY", "SUCCESS", null));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @DisplayName("같은 SUCCESS 콜백이 두 번 도착해도 이벤트는 한 번만 발행된다 (멱등).")
    @Test
    void handle_duplicateSuccess_publishesEventOnlyOnce() {
        Payment payment = savedInProgress(TX_KEY);

        handler.handle(callback(TX_KEY, "SUCCESS", null));
        handler.handle(callback(TX_KEY, "SUCCESS", null));

        assertThat(paymentRepository.find(payment.getId()).orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.SUCCESS);
        verify(eventPublisher, times(1)).publishEvent(any(PaymentSucceededEvent.class));
    }
}
