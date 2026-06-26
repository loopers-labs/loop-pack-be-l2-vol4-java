package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제 요청 흐름 조립. 트랜잭션 경계는 PaymentService(TX1/TX2)에 두고, 그 '사이'에서 PG를 호출한다
 * (원칙 3: 외부 호출은 트랜잭션 밖 → 커넥션/락 보유 최소화·장애 전파 차단).
 * 응답은 결과가 아니라 접수(ACK) — 최종 결과는 콜백/폴링으로 확정한다(원칙 2).
 */
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final PgPaymentClient pgClient;
    private final PaymentReconciler paymentReconciler;

    public PaymentInfo requestPayment(String loginId, Long orderId, CardType cardType, String cardNo) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        // TX1: PENDING 결제를 먼저 저장(record-first, 멱등 가드 포함)
        Payment payment = paymentService.createPending(user.getId(), orderId, cardType);

        // 외부 호출 (트랜잭션 밖, resilience4j: timeout/circuit + fallback)
        PgDto.Envelope<PgDto.TransactionResponse> response = pgClient.requestPayment(
            String.valueOf(user.getId()),
            new PgDto.PaymentRequest(toPgOrderId(orderId), cardType.name(), cardNo, payment.getAmount(), CALLBACK_URL));

        // TX2: PG가 접수(거래키)했으면 반영, 미확정이면 PENDING 유지(이후 reconcile 로 진실 확인)
        if (response.isSuccess() && response.data() != null && response.data().transactionKey() != null) {
            payment = paymentService.attachTransactionKey(payment.getId(), response.data().transactionKey());
        }
        return PaymentInfo.from(payment);
    }

    /** 수동 복구: 본인 결제를 PG에 조회해 상태를 보정한다(콜백 누락 시 사용자/운영 트리거). */
    public void reconcile(String loginId, Long paymentId) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
        Payment payment = paymentService.findOwned(paymentId, user.getId());
        paymentReconciler.reconcile(payment);
    }

    /**
     * PG 콜백 처리. 콜백 페이로드(status/reason)는 신뢰하지 않는다 — 위조 가능하므로 콜백은 '확인하라'는
     * 신호로만 쓰고, 진실은 reconcile 가 PG 재조회로 확정한다(위조 콜백을 보내도 PG가 실제 그 상태여야 반영).
     * (pg-simulator 가 서명을 제공하지 않아 HMAC/mTLS 검증은 미구현 — 실 PG라면 서명 검증 추가)
     */
    public void handleCallback(String transactionKey) {
        paymentService.findByTransactionKey(transactionKey)
            .ifPresent(paymentReconciler::reconcile);
    }

    /** 우리 Order id(Long) → PG orderId(문자열, 6자 이상 규칙). */
    private String toPgOrderId(Long orderId) {
        return String.format("%06d", orderId);
    }
}
