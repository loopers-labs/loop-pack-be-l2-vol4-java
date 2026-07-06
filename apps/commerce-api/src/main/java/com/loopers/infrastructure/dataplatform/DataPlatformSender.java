package com.loopers.infrastructure.dataplatform;

import com.loopers.application.payment.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 데이터 플랫폼 전송 — 현재는 mock(로그) 구현.
 *
 * <p>실무에서는 분석/집계용 외부 데이터 플랫폼으로 결제 이벤트를 전송하는 외부 I/O 다.
 * 외부 호출은 지연·실패가 잦으므로 호출 측(리스너)에서 {@code @Async} 로 요청 스레드와 분리한다.
 * Round 7 Step 2 에서 이 전송이 Kafka 발행으로 확장/대체된다.
 */
@Slf4j
@Component
public class DataPlatformSender {

    public void sendPaymentCompleted(PaymentCompletedEvent event) {
        // TODO(Step 2): 외부 데이터 플랫폼 API 호출 → Kafka 발행으로 확장
        log.info("[DataPlatform] 결제 완료 전송(mock) — orderId={}, transactionKey={}, amount={}",
            event.orderId(), event.transactionKey(), event.amount());
    }
}
