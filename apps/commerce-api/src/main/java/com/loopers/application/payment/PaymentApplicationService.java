package com.loopers.application.payment;

import com.loopers.application.order.OrderTransactionService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgGateway;
import com.loopers.domain.payment.PgIndeterminateException;
import com.loopers.domain.payment.PgRequestRejectedException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 결제 유스케이스 Application Service — 오케스트레이터.
 *
 * <p>pg-simulator 비동기 결제(requestPayment + handleCallback)를 조율한다 —
 * 요청 시 즉시 transactionKey(PENDING)를 받고, 최종 결과는 콜백/대사로 확정한다.
 *
 * <h2>requestPayment 순서</h2>
 * <ol>
 *   <li>검증 (TX readonly) — 소유자 / PENDING 상태 / 금액</li>
 *   <li>자원 점유 (TX2a) — 재고 차감 + 쿠폰 확정 + 주문 PAYMENT_IN_PROGRESS</li>
 *   <li>결제 기록 생성 (TX2b) — PaymentModel REQUESTED</li>
 *   <li>PG 요청 (트랜잭션 밖, CB + Feign timeout) — transactionKey 수신</li>
 *   <li>실패 시: PaymentModel FAILED + 자원 보상 (releaseAndFail)</li>
 * </ol>
 *
 * <h2>handleCallback 설계 포인트</h2>
 * <ul>
 *   <li>단일 트랜잭션 — PaymentModel 상태 전이와 주문 상태 전이를 원자적으로 처리.</li>
 *   <li>멱등성 — {@code isTerminal()} 으로 이미 처리된 콜백을 무시한다.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentApplicationService {

    private final OrderTransactionService orderTransactionService;
    private final PaymentService paymentService;
    private final PgGateway pgGateway;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Lazy
    @Autowired
    private PaymentApplicationService self;

    @Value("${pg.callback-base-url}")
    private String callbackBaseUrl;

    // ──────────────────────────────────────────────────────────────────────
    // pg-simulator 비동기 결제 요청
    // ──────────────────────────────────────────────────────────────────────

    public PaymentRequestInfo requestPayment(Long userId, Long orderId, CardType cardType, String cardNo, Long amount) {
        // 1. 소유자 / 상태 / 금액 위변조 검증
        orderTransactionService.validateConfirmable(userId, orderId, amount);

        // 2. 자원 점유 — 실패 시 견적 폐기(PENDING → FAILED)
        try {
            orderTransactionService.bindResources(orderId);
        } catch (CoreException e) {
            orderTransactionService.markOrderFailed(orderId);
            throw e;
        }

        // 3. 결제 시도 기록 (REQUESTED) — PG 호출 전 저장해 결제 시도 자체를 보존
        try {
            paymentService.createRequested(orderId, amount);
        } catch (Exception e) {
            // DB 저장 실패 — 점유 자원 원복
            orderTransactionService.releaseAndFail(orderId);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 기록 저장 중 오류가 발생했습니다.", e);
        }

        // 4. PG 요청 (CircuitBreaker + 500-only 재시도, 트랜잭션 밖)
        String callbackUrl = callbackBaseUrl + "/api/v1/payments/callback";
        try {
            String transactionKey = pgGateway.requestPayment(
                userId.toString(), orderId, cardType, cardNo, amount, callbackUrl);
            paymentService.storePendingTransactionKey(orderId, transactionKey);
            return new PaymentRequestInfo(transactionKey);

        } catch (PgRequestRejectedException e) {
            // 500 재시도 소진 = 트랜잭션 미생성 확정 → 결제 FAILED + 자원 원복 (단일 트랜잭션)
            self.handleRequestFailure(orderId, "PG 요청 거부");
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청에 실패했습니다.", e);

        } catch (PgIndeterminateException e) {
            // 타임아웃/연결오류 = 요청은 나갔으나 응답 미수신 → 결과 미확정 → 실패 처리 금지.
            // (서킷 open 은 요청 미전송이라 미확정이 아니며 위 PgRequestRejected 로 처리된다.)
            // 결제는 REQUESTED, 주문은 PAYMENT_IN_PROGRESS 로 남겨 콜백/대사 스케줄러가 보정한다.
            log.warn("[Payment] PG 결과 미확정 — 스케줄러 보정 대기. orderId={}, cause={}", orderId, e.getMessage());
            return new PaymentRequestInfo(null);
        }
    }

    /**
     * PG 요청 실패 시 결제 FAILED 마킹 + 자원 원복을 단일 트랜잭션으로 처리.
     *
     * <p>자기 주입({@code self})을 통해 호출해야 Spring 프록시가 {@code @Transactional} 을 적용한다.
     */
    @Transactional
    public void handleRequestFailure(Long orderId, String reason) {
        paymentService.markFailedOnRequest(orderId, reason);
        orderTransactionService.releaseAndFail(orderId);
    }

    // ──────────────────────────────────────────────────────────────────────
    // pg-simulator 비동기 콜백 수신
    // ──────────────────────────────────────────────────────────────────────

    /**
     * pg-simulator 콜백 처리.
     *
     * <p><strong>원자성</strong>: PaymentModel 상태 전이 + 주문 상태 전이를 단일 트랜잭션으로 묶는다.
     * {@link OrderTransactionService#completePayment} / {@link OrderTransactionService#releaseAndFail}
     * 은 REQUIRED 전파이므로 이 메서드의 트랜잭션에 참여한다.
     *
     * <p><strong>멱등성</strong>: {@link PaymentModel#isTerminal()} 이 true 면 이미 처리된 콜백.
     * 200 으로 응답하고 재처리하지 않는다.
     */
    @Transactional
    public void handleCallback(String transactionKey, Long orderId, String status, String reason) {
        Optional<PaymentModel> paymentOpt = paymentRepository.findByOrderId(orderId);
        if (paymentOpt.isEmpty()) {
            return;
        }
        PaymentModel payment = paymentOpt.get();
        if (payment.isTerminal()) {
            return;
        }

        if ("SUCCESS".equals(status)) {
            payment.markSuccess(transactionKey);             // JPA dirty checking → 자동 UPDATE
            orderTransactionService.completePayment(orderId);
            // 결제 확정 부가작업(데이터 플랫폼 전송 등)은 이벤트로 분리 — 커밋 후 리스너가 비동기 처리
            eventPublisher.publishEvent(new PaymentCompletedEvent(orderId, transactionKey, payment.getAmount()));
        } else {
            payment.markFailed(reason != null ? reason : "결제 실패");
            orderTransactionService.releaseAndFail(orderId);
        }
    }
}
