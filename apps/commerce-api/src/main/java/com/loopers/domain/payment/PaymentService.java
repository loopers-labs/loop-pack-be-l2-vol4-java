package com.loopers.domain.payment;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 결제 접수. 동일 주문에 PENDING 결제가 있으면 재사용(멱등),
     * 이미 확정된 결제면 CONFLICT. 동시 요청 경합은 DB UNIQUE(order_id)가 차단한다.
     */
    @Transactional
    public PaymentModel createPending(Long orderId, Long userId, CardType cardType, Money amount) {
        return paymentRepository.findByOrderId(orderId)
            .map(existing -> {
                if (existing.getStatus() != PaymentStatus.PENDING) {
                    throw new CoreException(ErrorType.CONFLICT, "[orderId = " + orderId + "] 이미 처리된 결제가 있습니다.");
                }
                return existing;
            })
            .orElseGet(() -> paymentRepository.save(new PaymentModel(orderId, userId, cardType, amount)));
    }

    @Transactional
    public void assignTransactionKey(Long orderId, String transactionKey) {
        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
        payment.assignTransactionKey(transactionKey);
    }

    /**
     * 콜백·폴링으로 받은 결과를 확정하고 도메인 이벤트를 발행한다.
     * 이미 확정된 결제는 no-op — 중복 콜백/폴링에도 이벤트를 재발행하지 않는다(멱등).
     */
    @Transactional
    public void confirm(String transactionKey, boolean success, String reason) {
        PaymentModel payment = paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        if (success) {
            payment.markSuccess();
            eventPublisher.publishEvent(new PaymentCompleted(payment.getOrderId()));
        } else {
            payment.markFailed(reason);
            eventPublisher.publishEvent(new PaymentFailed(payment.getOrderId(), reason));
        }
    }

    @Transactional(readOnly = true)
    public PaymentModel getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public PaymentModel getByTransactionKey(String transactionKey) {
        return paymentRepository.findByTransactionKey(transactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[transactionKey = " + transactionKey + "] 결제를 찾을 수 없습니다."));
    }

    /** 거래키 없는 결제를 주문 기준으로 실패 확정한다(PG 미접수 복구용). 이미 확정됐으면 멱등 no-op. */
    @Transactional
    public void failByOrderId(Long orderId, String reason) {
        PaymentModel payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[orderId = " + orderId + "] 결제를 찾을 수 없습니다."));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.markFailed(reason);
        eventPublisher.publishEvent(new PaymentFailed(orderId, reason));
    }
}
