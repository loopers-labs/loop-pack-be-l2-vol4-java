package com.loopers.infrastructure.dataplatform;

import com.loopers.domain.dataplatform.DataPlatformPayload;
import com.loopers.domain.dataplatform.DataPlatformSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingDataPlatformSender implements DataPlatformSender {

    @Override
    public void send(DataPlatformPayload payload) {
        // Step 2 에서 Kafka/Outbox 구현체로 교체
        log.info("[DataPlatform] 주문 전송 orderId={} userId={} finalAmount={} itemCount={}",
            payload.orderId(), payload.userId(), payload.finalAmount(), payload.items().size());
    }
}
