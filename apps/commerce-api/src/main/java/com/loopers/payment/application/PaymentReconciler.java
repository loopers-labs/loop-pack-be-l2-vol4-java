package com.loopers.payment.application;

import com.loopers.payment.domain.Payment;
import com.loopers.payment.domain.PaymentGateway;
import com.loopers.payment.domain.PaymentGatewayResult;
import com.loopers.payment.domain.PaymentRepository;
import com.loopers.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * 정합성 보정. 콜백이 무보증이라 어긋난 상태를 PG 조회로 맞춘다 — 재결제가 아니라 기존 거래 조회 기반.
 * 확정은 콜백과 같은 공유 진입점({@link PaymentResultHandler})을 호출해 보상 로직이 갈라지지 않게 한다.
 *
 * <p>두 기준으로 보정한다. <b>키 기준 보정</b>(콜백 유실)은 transactionKey 로 조회해 확정하고,
 * <b>주문 기준 보정</b>(TX2 전 크래시)은 orderId 로 조회해 거래가 있으면 키를 채워 확정하고 없으면 FAILED 로 정리한다.
 * 조회 실패는 FAILED 로 단정하지 않고 다음 주기로 넘긴다(성공한 결제를 죽이지 않기 위함).
 */
@Slf4j
@RequiredArgsConstructor
@Profile("!test")
@Component
public class PaymentReconciler {

    private static final Duration STALE_THRESHOLD = Duration.ofSeconds(30);
    private static final Duration ABANDON_THRESHOLD = Duration.ofHours(1);

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentResultHandler paymentResultHandler;
    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 30_000)
    public void reconcile() {
        reconcileWithKey();
        reconcileWithoutKey();
    }

    void reconcileWithKey() {
        ZonedDateTime before = ZonedDateTime.now().minus(STALE_THRESHOLD);
        for (Payment payment : paymentRepository.findStalePendingWithKey(before)) {
            try {
                PaymentGatewayResult result = paymentGateway.inquire(payment.getUserId(), payment.getTransactionKey());
                if (result.status() == PaymentStatus.PENDING) {
                    continue; // PG 도 아직 미확정 — 다음 주기
                }
                confirm(payment, payment.getTransactionKey(), result.status(), result.reason());
            } catch (Exception e) {
                handleInquiryFailure(payment, e);
            }
        }
    }

    void reconcileWithoutKey() {
        ZonedDateTime before = ZonedDateTime.now().minus(STALE_THRESHOLD);
        for (Payment payment : paymentRepository.findStalePendingWithoutKey(before)) {
            try {
                List<PaymentGatewayResult> transactions =
                        paymentGateway.inquireByOrder(payment.getUserId(), payment.getOrderNumber());
                if (transactions.isEmpty()) {
                    // PG 에 거래 없음 — 돈이 움직이지 않은 미접수. FAILED 정리+보상을 공유 진입점에 맡긴다.
                    paymentResultHandler.handleAbsentTransaction(payment.getId());
                    log.info("주문 기준 보정: 무거래 정리 orderNumber={} paymentId={}", payment.getOrderNumber(), payment.getId());
                    continue;
                }
                // 거래 있음 — 키를 채워 키 기준 보정 경로로 합류시킨다(활성 PENDING 은 주문당 1건이라 거래도 최대 1건).
                PaymentGatewayResult transaction = transactions.getFirst();
                paymentService.assignTransaction(payment.getId(), transaction.transactionKey(), transaction.pgProvider());
                if (transaction.status() == PaymentStatus.PENDING) {
                    continue; // 키만 채우고 확정은 다음 주기의 키 기준 보정에 위임
                }
                confirm(payment, transaction.transactionKey(), transaction.status(), transaction.reason());
            } catch (Exception e) {
                handleInquiryFailure(payment, e);
            }
        }
    }

    private void confirm(Payment payment, String transactionKey, PaymentStatus status, String reason) {
        paymentResultHandler.handle(new PaymentCommand.Confirm(
                transactionKey, payment.getOrderNumber(), payment.getAmount().value(), status, reason));
        log.info("정합성 보정 확정 orderNumber={} transactionKey={} status={}", payment.getOrderNumber(), transactionKey, status);
    }

    private void handleInquiryFailure(Payment payment, Exception e) {
        // 조회 실패: 충분히 오래됐으면 포기(ABANDONED)+알림, 아니면 다음 주기로 재시도(일시 장애일 수 있음).
        if (payment.getCreatedAt() != null
                && payment.getCreatedAt().isBefore(ZonedDateTime.now().minus(ABANDON_THRESHOLD))) {
            // 상세 예외는 로그로만 남기고, reason 컬럼에는 짧은 사유만 저장(컬럼 길이 초과 방지).
            log.error("정합성 보정 실패로 ABANDONED 전환 orderNumber={} cause={}", payment.getOrderNumber(), e.toString());
            paymentService.abandon(payment.getId(), "PG 조회 불가로 정합성 보정 실패");
        } else {
            log.warn("정합성 보정 보류 orderNumber={} 사유={}", payment.getOrderNumber(), e.getMessage());
        }
    }
}
