package com.loopers.application.useractionlog;

import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.domain.payment.event.PaymentFailedEvent;
import com.loopers.domain.useractionlog.UserActionLogModel;
import com.loopers.domain.useractionlog.UserActionLogRepository;
import com.loopers.domain.useractionlog.UserActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문/결제 부가 로직(유저 행동 로깅) — 커밋 후 별도 트랜잭션에서 처리한다.
 * 로깅 실패가 이미 커밋된 주문/결제 처리에 영향을 주지 않도록 예외를 흡수한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class UserActionLogEventListener {

    private final UserActionLogRepository userActionLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderPlacedEvent event) {
        save(event.userId(), UserActionType.ORDER_PLACED, event.orderId(), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentCompletedEvent event) {
        save(event.userId(), UserActionType.PAYMENT_COMPLETED, event.paymentId(), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PaymentFailedEvent event) {
        save(event.userId(), UserActionType.PAYMENT_FAILED, event.paymentId(), event.failureCode());
    }

    private void save(Long userId, UserActionType actionType, Long referenceId, String detail) {
        try {
            userActionLogRepository.save(new UserActionLogModel(userId, actionType, referenceId, detail));
        } catch (Exception e) {
            log.error("유저 행동 로그 저장 실패 — actionType={}, userId={}, referenceId={}",
                actionType, userId, referenceId, e);
        }
    }
}
