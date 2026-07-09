package com.loopers.application.notification;

import com.loopers.domain.payment.event.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료 알림(메일 등) 발송. 부가 로직이라 이벤트로 분리한다.
 *
 * <ul>
 *   <li><b>AFTER_COMMIT</b>: 결제 성공이 실제 커밋된 뒤에만 알린다(롤백된 결제에 "완료" 메일이 가면 안 됨).</li>
 *   <li><b>@Async</b>: 메일 발송은 외부 I/O(느림)라 요청 스레드를 잡으면 안 된다 → 별도 스레드로 빼 응답 지연을 없앤다.
 *       알림 실패는 결제를 되돌리지 못한다(best-effort).</li>
 * </ul>
 *
 * <p>이 과제 범위에선 실제 SMTP 를 붙이지 않고 발송 의도만 로그로 남긴다(stub). 절대 유실되면 안 되는 알림이라면
 * @Async 만으로는 부족하고 Outbox/큐를 거쳐야 한다 — "유실 허용도" 가 메커니즘을 가른다.</p>
 */
@Slf4j
@Component
public class NotificationEventListener {

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("[알림] 결제 완료 메일 발송(stub) orderId={}, userId={}, amount={}",
                event.orderId(), event.userId(), event.amount());
    }
}
