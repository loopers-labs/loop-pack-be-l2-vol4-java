package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PENDING 결제를 PG에 조회해 진실을 맞춘다(reconcile). 콜백과 같은 진실을 다른 채널(pull)로 받는 것 —
 * 콜백 유실/타임아웃에 대한 안전망. 외부 호출이 끼므로 트랜잭션 밖에서 조회하고, 반영은 applyResult(tx)에 위임한다.
 * - 거래키가 있으면: GET /payments/{key} 로 상태 조회 → 터미널이면 반영.
 * - 거래키가 없으면(요청 타임아웃으로 키 미수신): GET /payments?orderId= 로 거래를 입양(키 부여) 후 반영.
 * 추측으로 단정하지 않는다 — PG가 아직 PENDING이거나 조회 실패면 다음 기회로 미룬다.
 */
@RequiredArgsConstructor
@Component
public class PaymentReconciler {

    private final PgPaymentClient pgClient;
    private final PaymentService paymentService;

    public void reconcile(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        String userId = String.valueOf(payment.getUserId());

        if (payment.getTransactionKey() != null) {
            PgDto.Envelope<PgDto.TransactionDetailResponse> res = pgClient.getTransaction(userId, payment.getTransactionKey());
            if (res.isSuccess() && res.data() != null) {
                applyIfTerminal(payment.getTransactionKey(), res.data().status(), res.data().reason());
            }
            return;
        }

        // 타임아웃으로 거래키를 못 받은 경우: orderId 로 거래를 찾아 입양
        PgDto.Envelope<PgDto.OrderResponse> res = pgClient.findByOrderId(userId, PgDto.orderId(payment.getOrderId()));
        if (res.isSuccess() && res.data() != null
            && res.data().transactions() != null && !res.data().transactions().isEmpty()) {
            PgDto.TransactionResponse tx = res.data().transactions().get(0);
            paymentService.attachTransactionKey(payment.getId(), tx.transactionKey());
            applyIfTerminal(tx.transactionKey(), tx.status(), tx.reason());
        }
    }

    private void applyIfTerminal(String transactionKey, String status, String reason) {
        PaymentStatus result;
        try {
            result = PaymentStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            return; // 알 수 없는 상태 — 무시
        }
        if (result == PaymentStatus.PENDING) {
            return; // PG가 아직 처리 중 — 다음 폴링에
        }
        paymentService.applyResult(transactionKey, result, reason);
    }
}
