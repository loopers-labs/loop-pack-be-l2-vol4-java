package com.loopers.infrastructure.event.scheduler;

import com.loopers.application.event.relay.EventRelayWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "commerce.workers.event-relay.enabled", havingValue = "true")
public class EventRelayWorkerScheduler {

    private final EventRelayWorker eventRelayWorker;

    @Scheduled(
        initialDelayString = "${commerce.workers.event-relay.initial-delay-ms:5000}",
        fixedDelayString = "${commerce.workers.event-relay.fixed-delay-ms:5000}"
    )
    public void relayPendingEvents() {
        List<EventRelayWorker.RelayResult> results = eventRelayWorker.relayPendingEvents();
        log.info("event relay worker processed pending events. count={}", results.size());
    }
}
