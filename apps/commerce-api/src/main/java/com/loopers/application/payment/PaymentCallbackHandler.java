package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayRouter;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgResponse;
import com.loopers.domain.payment.PgStatus;
import com.loopers.interfaces.api.payment.PaymentCallbackV1Dto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PG 콜백 수신 처리. transactionKey 로 Payment 를 매칭하고, **PG 상태를 한 번 더 조회해 재확인** 후 상태 전이.
 *
 * 콜백 신뢰 X (1팀 Q5):
 *  - 콜백 body 가 위조·중복일 수 있고, PG 내부 상태와 어긋날 수도 있다.
 *  - 그래서 callback 받은 직후 PG.getStatus() 로 한 번 더 확인해 그 결과를 신뢰원으로 삼는다.
 *  - 재확인 호출 자체가 실패하면(통신 오류) 콜백 body 로 fallback 처리 (안전망 우선).
 *
 * 멱등성 보장:
 *  - 같은 콜백 두 번 → markSuccess/markFailed 내부 멱등으로 흡수
 *  - 콜백이 결제 응답보다 빠름 → Payment 매칭 실패 → 무시 (폴링이 확정)
 *
 * PG 가 200 외 응답에 재시도할 수 있어 어떤 경우에도 예외 전파 X.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentCallbackHandler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentGatewayRouter router;

    public void handle(PaymentCallbackV1Dto callback) {
        if (callback.transactionKey() == null || callback.transactionKey().isBlank()) {
            log.warn("[Callback] transactionKey 가 비어있는 콜백 — 무시");
            return;
        }

        Payment payment = paymentRepository.findByTransactionKey(callback.transactionKey()).orElse(null);
        if (payment == null) {
            log.warn("[Callback] 매칭되는 Payment 없음 (응답 < 콜백 race 가능). transactionKey={}", callback.transactionKey());
            return;
        }

        // 콜백 신뢰 X — PG 에 직접 조회해 신뢰원으로 삼는다.
        PgStatus verifiedStatus;
        String verifiedReason;
        try {
            PaymentGateway gateway = router.gatewayFor(payment.getProvider());
            PgResponse verified = gateway.getStatus(callback.transactionKey(), payment.getUserId());
            verifiedStatus = verified.status();
            verifiedReason = verified.reason();
            log.debug("[Callback] PG 재확인 결과 status={}, reason={}", verifiedStatus, verifiedReason);
        } catch (Exception e) {
            // PG 재조회 실패 → 콜백 body 로 fallback (정합성보단 일단 처리 우선)
            log.warn("[Callback] PG 재확인 실패, 콜백 body 로 fallback. txKey={}, error={}",
                callback.transactionKey(), e.getMessage());
            verifiedStatus = parseStatusSafely(callback.status());
            verifiedReason = callback.reason();
            if (verifiedStatus == null) {
                log.warn("[Callback] status 파싱 불가 — 무시. txKey={}", callback.transactionKey());
                return;
            }
        }

        switch (verifiedStatus) {
            case SUCCESS -> paymentService.markSuccess(payment.getId());
            case FAILED -> paymentService.markFailed(payment.getId(), verifiedReason);
            case PENDING -> log.debug("[Callback] PG 는 아직 PENDING — 폴링 대기. txKey={}", callback.transactionKey());
        }
    }

    private PgStatus parseStatusSafely(String status) {
        if (status == null) return null;
        try {
            return PgStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
