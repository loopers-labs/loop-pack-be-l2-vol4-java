package com.loopers.application.ordering.queue;

import com.loopers.domain.ordering.queue.OrderQueueRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class OrderQueueAdmissionWorker {
    private final OrderQueueRepository orderQueueRepository;
    private final Duration tokenTtl;
    private final int admitBatchSize;

    @Autowired
    public OrderQueueAdmissionWorker(
        OrderQueueRepository orderQueueRepository,
        @Value("${commerce.workers.order-queue.token-ttl:5m}") Duration tokenTtl,
        @Value("${commerce.workers.order-queue.admit-batch-size:10}") int admitBatchSize
    ) {
        this.orderQueueRepository = orderQueueRepository;
        this.tokenTtl = tokenTtl;
        this.admitBatchSize = admitBatchSize;
    }

    public List<OrderQueueRepository.Admitted> admitNext() {
        return orderQueueRepository.admitNext(Math.max(admitBatchSize, 0), tokenTtl);
    }
}
