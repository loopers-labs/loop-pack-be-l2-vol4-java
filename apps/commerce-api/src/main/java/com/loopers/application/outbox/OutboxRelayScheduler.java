package com.loopers.application.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
    prefix = "loopers.outbox",
    name = "relay-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class OutboxRelayScheduler {

    private final OutboxRelayService outboxRelayService;

    @Scheduled(fixedDelayString = "${loopers.outbox.relay-delay:5s}")
    public void relay() {
        outboxRelayService.relayReadyEvents();
    }
}
