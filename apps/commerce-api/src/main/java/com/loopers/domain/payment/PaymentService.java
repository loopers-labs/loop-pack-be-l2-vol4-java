package com.loopers.domain.payment;

import com.loopers.domain.payment.event.PaymentFailedEvent;
import com.loopers.domain.payment.event.PaymentSucceededEvent;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 도메인 서비스. 상태 전이 메서드들을 각각 짧은 트랜잭션으로 노출해
 * PaymentFacade 가 PG 호출(외부, 트랜잭션 밖) 결과를 받아 마무리 전이만 짧게 커밋하게 한다.
 *
 * 이벤트 발행:
 *  - markSuccess  → PaymentSucceededEvent (Order PAID 전이 트리거)
 *  - markFailed   → PaymentFailedEvent    (Order FAILED 전이 + 재고 복구 트리거)
 *  - markUnknown  → 이벤트 X (확정 안 됐으므로 외부 효과 미발생, 폴링이 확정 시 다시 호출됨)
 */
@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 결제 시도 생성. 한 주문에 이미 진행 중(REQUESTED/IN_PROGRESS/UNKNOWN) 결제가 있으면 CONFLICT.
     * 중복 결제 차단의 1차 방어선 (orderId 기반 멱등).
     */
    @Transactional
    public Payment createRequested(Long orderId, Long userId, PgProvider provider, Money amount,
                                   CardType cardType, String cardLastFour) {
        if (!paymentRepository.findActiveByOrderId(orderId).isEmpty()) {
            throw new CoreException(ErrorType.CONFLICT,
                "[orderId = " + orderId + "] 이미 진행 중인 결제가 있습니다.");
        }
        Payment payment = Payment.request(orderId, userId, provider, amount, cardType, cardLastFour);
        return paymentRepository.save(payment);
    }

    @Transactional
    public void markInProgress(Long paymentId, String transactionKey) {
        Payment payment = getPayment(paymentId);
        payment.markInProgress(transactionKey);
    }

    @Transactional
    public void markSuccess(Long paymentId) {
        Payment payment = getPayment(paymentId);
        PaymentStatus before = payment.getStatus();
        payment.markSuccess();   // 도메인 멱등 + 위험 전이 차단
        // 멱등 — 이미 SUCCESS 였으면 이벤트 중복 발행 방지
        if (before != PaymentStatus.SUCCESS) {
            eventPublisher.publishEvent(new PaymentSucceededEvent(
                payment.getId(), payment.getOrderId(), payment.getUserId(), payment.getAmount()
            ));
        }
    }

    @Transactional
    public void markFailed(Long paymentId, String reason) {
        Payment payment = getPayment(paymentId);
        PaymentStatus before = payment.getStatus();
        payment.markFailed(reason);
        // 멱등 — 이미 FAILED 였으면 이벤트 중복 발행 방지
        if (before != PaymentStatus.FAILED) {
            // RECOVERABLE/TERMINAL 분류를 reason 기반으로 자동 결정해 핸들러에 위임.
            // 호출자(Facade/Callback/Reconciler) 는 분류를 신경 쓸 필요 없다.
            PaymentFailureCategory category = PaymentFailureCategory.classify(reason);
            eventPublisher.publishEvent(new PaymentFailedEvent(
                payment.getId(), payment.getOrderId(), payment.getUserId(), reason, category
            ));
        }
    }

    @Transactional
    public void markUnknown(Long paymentId, String reason) {
        Payment payment = getPayment(paymentId);
        payment.markUnknown(reason);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId) {
        return paymentRepository.find(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + paymentId + "] 결제를 찾을 수 없습니다."));
    }
}
