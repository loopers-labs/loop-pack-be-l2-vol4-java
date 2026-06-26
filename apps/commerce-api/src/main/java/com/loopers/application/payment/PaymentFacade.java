package com.loopers.application.payment;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgClientException;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 결제 유스케이스 오케스트레이터. DB 트랜잭션({@link PaymentApplicationService})과 외부 PG 호출({@link PgClient})을
 * 분리해 순차 조합한다: 접수(commit) → PG 호출(트랜잭션 밖) → 거래키 반영(commit).
 */
@Component
public class PaymentFacade {

    private static final Logger log = LoggerFactory.getLogger(PaymentFacade.class);

    private final PaymentApplicationService paymentApplicationService;
    private final PaymentReconciler paymentReconciler;
    private final PgClient pgClient;
    private final String callbackUrl;

    public PaymentFacade(
        PaymentApplicationService paymentApplicationService,
        PaymentReconciler paymentReconciler,
        PgClient pgClient,
        @Value("${pg.callback-url}") String callbackUrl
    ) {
        this.paymentApplicationService = paymentApplicationService;
        this.paymentReconciler = paymentReconciler;
        this.pgClient = pgClient;
        this.callbackUrl = callbackUrl;
    }

    public PaymentInfo pay(Long userId, PaymentCommand command) {
        PaymentModel payment = paymentApplicationService.register(userId, command);

        if (payment.needsPgRequest()) {
            requestToPg(userId, payment);
            payment = paymentApplicationService.getPayment(payment.getId());
        }
        return PaymentInfo.from(payment);
    }

    /** PG 콜백 수신 처리. 결과를 결제건·주문에 반영한다. */
    public void handleCallback(String transactionKey, PgTransactionStatus status, String reason) {
        paymentApplicationService.confirm(transactionKey, status, reason);
    }

    /** 수동 동기화: 특정 결제건을 PG 와 재조정한 뒤 최신 상태를 반환한다. */
    public PaymentInfo syncPayment(Long userId, Long paymentId) {
        PaymentModel payment = paymentApplicationService.getPayment(paymentId);
        if (!payment.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }
        return PaymentInfo.from(paymentReconciler.reconcile(payment));
    }

    private void requestToPg(Long userId, PaymentModel payment) {
        try {
            PgPaymentResult result = pgClient.requestPayment(new PgPaymentCommand(
                String.valueOf(userId),
                String.format("%06d", payment.getOrderId()),
                payment.getCardType(),
                payment.getCardNo(),
                payment.getAmount(),
                callbackUrl
            ));
            paymentApplicationService.attachTransactionKey(payment.getId(), result.transactionKey());
        } catch (PgClientException e) {
            // 타임아웃/호출 실패: 실제로는 PG 가 접수했을 수 있으므로 즉시 실패 처리하지 않고
            // PENDING 으로 두어 Phase 5(콜백/폴링 재조정) 에서 실제 상태를 확인해 반영한다.
            log.warn("PG 결제 요청 실패 - 재조정 대상으로 둠. paymentId={}", payment.getId(), e);
        }
    }
}
