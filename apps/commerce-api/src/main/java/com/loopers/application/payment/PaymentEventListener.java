package com.loopers.application.payment;

import com.loopers.infrastructure.dataplatform.DataPlatformSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료 부가작업 리스너 — 데이터 플랫폼 전송.
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} — 결제 확정 트랜잭션이 커밋된 뒤에만 반응한다(롤백 시 미전송).
 * {@link Async} — 전송은 외부 I/O(지연·실패 가능)라 별도 스레드로 분리해 결제 콜백 응답을 블로킹하지 않는다.
 * 별도 스레드라 예외도 요청 스레드로 전파되지 않지만, 관측을 위해 여기서 잡아 로그로 남긴다.
 *
 * <p>DB 를 쓰지 않으므로 {@code @Transactional} 은 두지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentEventListener {

    private final DataPlatformSender dataPlatformSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        try {
            dataPlatformSender.sendPaymentCompleted(event);
        } catch (Exception e) {
            log.warn("[Payment] 데이터 플랫폼 전송 실패 — orderId={}, transactionKey={}",
                event.orderId(), event.transactionKey(), e);
        }
    }
}
