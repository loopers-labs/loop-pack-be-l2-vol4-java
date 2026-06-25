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

/**
 * 정합성 보정(대사). 콜백이 무보증이라 어긋난 상태를 PG 조회로 맞춘다 — 재결제가 아니라 기존 거래 조회 기반.
 * 확정은 콜백과 같은 공유 진입점({@link PaymentResultHandler})을 호출해 보상 로직이 갈라지지 않게 한다.
 *
 * <p>현재는 <b>키 보유 sweep</b>(콜백 유실 회수)만 수행한다. 키 없음 sweep(TX2 전 크래시)은 후속 작업.
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
    }

    void reconcileWithKey() {
        ZonedDateTime before = ZonedDateTime.now().minus(STALE_THRESHOLD);
        for (Payment payment : paymentRepository.findStalePendingWithKey(before)) {
            try {
                PaymentGatewayResult result = paymentGateway.inquire(payment.getUserId(), payment.getTransactionKey());
                if (result.status() == PaymentStatus.PENDING) {
                    continue; // PG 도 아직 미확정 — 다음 주기
                }
                paymentResultHandler.handle(new PaymentCommand.Confirm(
                        payment.getTransactionKey(), payment.getOrderNumber(), payment.getAmount().value(),
                        result.status(), result.reason()));
                log.info("대사 확정 orderNumber={} transactionKey={} status={}",
                        payment.getOrderNumber(), payment.getTransactionKey(), result.status());
            } catch (Exception e) {
                // 조회 실패: 충분히 오래됐으면 포기(ABANDONED)+알림, 아니면 다음 주기로 재시도(일시 장애일 수 있음).
                if (payment.getCreatedAt() != null
                        && payment.getCreatedAt().isBefore(ZonedDateTime.now().minus(ABANDON_THRESHOLD))) {
                    // 상세 예외는 로그로만 남기고, reason 컬럼에는 짧은 사유만 저장(컬럼 길이 초과 방지).
                    log.error("대사 실패로 ABANDONED 전환 orderNumber={} cause={}", payment.getOrderNumber(), e.toString());
                    paymentService.abandon(payment.getId(), "PG 조회 불가로 정합성 보정 회수 실패");
                } else {
                    log.warn("대사 보류 orderNumber={} 사유={}", payment.getOrderNumber(), e.getMessage());
                }
            }
        }
    }
}
