package com.loopers.application.eventpublish;

import com.loopers.domain.eventpublish.OutboxMessage;
import com.loopers.domain.eventpublish.OutboxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 폴링 Relay — 주기적으로 PENDING 메시지를 꺼내 Kafka 로 발행하고, 성공한 것만 SENT 로 마킹한다.
 * <p>
 * 실패한 메시지는 상태를 그대로 PENDING 으로 두고 retryCount 만 증가 — 다음 폴링에서 다시 시도된다.
 * <p>
 * 단일 인스턴스 가정. 다중 인스턴스 운영 시 SELECT ... FOR UPDATE SKIP LOCKED 또는 ShedLock 검토 필요.
 */
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxService outboxService;
    private final OutboxRelayPort relayPort;

    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}", initialDelay = 5_000)
    public void relayPending() {
        List<OutboxMessage> batch = outboxService.loadPendingBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        int sent = 0;
        int failed = 0;
        for (OutboxMessage message : batch) {
            try {
                relayPort.publish(message);
                outboxService.markSent(message.getId());
                sent++;
            } catch (Exception e) {
                log.warn("Outbox 발행 실패 — 다음 폴링에서 재시도. id={}, eventType={}, cause={}",
                    message.getId(), message.getEventType(), e.getMessage());
                outboxService.markFailed(message.getId(), e.getMessage());
                failed++;
            }
        }
        if (failed > 0) {
            log.info("Outbox relay 완료 — sent={}, failed={}", sent, failed);
        }
    }
}
