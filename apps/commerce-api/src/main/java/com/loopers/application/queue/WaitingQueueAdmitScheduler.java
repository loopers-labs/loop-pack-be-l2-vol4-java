package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
    prefix = "loopers.waiting-queue",
    name = "scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class WaitingQueueAdmitScheduler {

    private final WaitingQueueAdmitService waitingQueueAdmitService;

    @Scheduled(fixedDelayString = "${loopers.waiting-queue.admit-delay:100ms}")
    public void admit() {
        waitingQueueAdmitService.admit();
    }
}
