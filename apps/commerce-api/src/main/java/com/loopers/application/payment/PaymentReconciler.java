package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgTransactionDetail;
import com.loopers.domain.payment.PgTransactionStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 콜백이 오지 않거나 PG 요청이 타임아웃된 PENDING 결제를, PG 상태 확인 API 로 조회해 실제 상태를 반영한다.
 * PG 호출은 트랜잭션 밖에서 수행하고, 결과 반영만 {@link PaymentApplicationService#confirm} (트랜잭션)에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);

    private final PaymentRepository paymentRepository;
    private final PaymentApplicationService paymentApplicationService;
    private final PgClient pgClient;

    /** 모든 PENDING 결제를 재조정한다. (스케줄러용) */
    public void reconcileAllPending() {
        List<PaymentModel> pendings = paymentRepository.findByStatus(PaymentStatus.PENDING);
        for (PaymentModel payment : pendings) {
            try {
                reconcile(payment);
            } catch (Exception e) {
                log.warn("결제 재조정 실패. paymentId={}", payment.getId(), e);
            }
        }
    }

    /** 단건 재조정 후 최신 결제 상태를 반환한다. (수동 API용) */
    public PaymentModel reconcile(PaymentModel payment) {
        if (!payment.isPending()) {
            return payment;
        }
        String userId = String.valueOf(payment.getUserId());

        if (payment.getTransactionKey() != null) {
            // 거래키가 있으면 상태 확인 API 로 조회
            pgClient.getTransaction(userId, payment.getTransactionKey())
                .filter(detail -> detail.status() != PgTransactionStatus.PENDING)
                .ifPresent(detail -> paymentApplicationService.confirm(payment.getTransactionKey(), detail.status(), detail.reason()));
        } else {
            // 타임아웃/요청실패로 거래키가 없는 경우: orderId 로 PG 에서 실제 거래를 찾는다
            List<PgTransactionDetail> transactions =
                pgClient.findTransactionsByOrderId(userId, String.format("%06d", payment.getOrderId()));
            transactions.stream().findFirst().ifPresent(tx -> {
                paymentApplicationService.attachTransactionKey(payment.getId(), tx.transactionKey());
                if (tx.status() != PgTransactionStatus.PENDING) {
                    paymentApplicationService.confirm(tx.transactionKey(), tx.status(), tx.reason());
                }
            });
        }
        return paymentApplicationService.getPayment(payment.getId());
    }
}
