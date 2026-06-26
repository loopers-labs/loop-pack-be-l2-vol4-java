package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgStatus;
import com.loopers.domain.payment.PgTransactionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 상태 복구기.
 * 콜백 유실/요청 타임아웃으로 PENDING·PROCESSING 에 멈춘 결제를, PG에 직접 물어 결과를 맞춘다.
 * 체크리스트: "콜백이 오지 않더라도 일정 주기/수동 API 로 상태를 복구할 수 있다."
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryService {

    /** 처리지연(1~5s) + 콜백 여유. 이보다 최근에 갱신된 건은 아직 정상 흐름 중일 수 있어 스킵. */
    private static final long STALE_SECONDS = 10;

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentFacade paymentFacade;

    /** 30초마다 자동 복구 (앱 기동 30초 후 시작). 수동 트리거(recoverStuckPayments)도 동일 로직. */
    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void scheduledRecover() {
        int n = recoverStuckPayments();
        if (n > 0) {
            log.info("[복구] {}건의 결제 상태를 PG와 동기화했습니다.", n);
        }
    }

    /** 멈춘 결제를 찾아 PG 결과로 동기화. 동기화한 건수를 반환. */
    public int recoverStuckPayments() {
        List<PaymentModel> stuck = paymentRepository.findByStatusIn(
                List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING));

        ZonedDateTime threshold = ZonedDateTime.now().minusSeconds(STALE_SECONDS);
        int recovered = 0;

        for (PaymentModel payment : stuck) {
            if (payment.getUpdatedAt().isAfter(threshold)) {
                continue; // 아직 정상 흐름 중일 수 있음 — 다음 주기로
            }
            Optional<PgTransactionResult> result = queryPg(payment);
            if (result.isPresent() && result.get().status() != PgStatus.PENDING) {
                paymentFacade.reconcile(payment.getId(), result.get());
                recovered++;
            }
        }
        return recovered;
    }

    /** txKey 가 있으면 단건조회, 없으면(요청 타임아웃) orderId 로 조회. (트랜잭션 밖, 게이트웨이가 CB/폴백 처리) */
    private Optional<PgTransactionResult> queryPg(PaymentModel payment) {
        if (payment.getTransactionKey().isPresent()) {
            return paymentGateway.findByTransactionKey(payment.getTransactionKey().get());
        }
        return paymentGateway.findByOrderId(payment.getOrderId()).stream().findFirst();
    }
}
