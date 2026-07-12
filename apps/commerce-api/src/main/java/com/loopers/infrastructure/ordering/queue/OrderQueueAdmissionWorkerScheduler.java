package com.loopers.infrastructure.ordering.queue;

import com.loopers.application.ordering.queue.OrderQueueAdmissionWorker;
import com.loopers.domain.ordering.queue.OrderQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "commerce.workers.order-queue.enabled", havingValue = "true")
public class OrderQueueAdmissionWorkerScheduler {

    private final OrderQueueAdmissionWorker worker;

    @Scheduled(
        initialDelayString = "${commerce.workers.order-queue.initial-delay-ms:1000}",
        fixedDelayString = "${commerce.workers.order-queue.fixed-delay-ms:1000}"
    )
    public void admitWaitingUsers() {
        List<OrderQueueRepository.Admitted> admitted = worker.admitNext();
        log.info("order queue admission worker admitted waiting users. count={}", admitted.size());
    }
}
