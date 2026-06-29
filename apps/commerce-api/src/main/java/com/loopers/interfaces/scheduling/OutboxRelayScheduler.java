package com.loopers.interfaces.scheduling;

import com.loopers.application.outbox.OutboxRelay;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * outbox-relay.enabled=true 인 프로파일(dev/qa/prd)에서만 등록된다.
 * local/test 는 미설정 → 스케줄러 미등록 → 테스트 중 브로커 블로킹·레이스 방지. (Relay 로직 자체는 OutboxRelay 빈으로 항상 존재)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox-relay.enabled", havingValue = "true")
public class OutboxRelayScheduler {

    private final OutboxRelay outboxRelay;

    @Scheduled(fixedDelayString = "${outbox-relay.scan-interval}")
    public void relay() {
        outboxRelay.relay();
    }
}
