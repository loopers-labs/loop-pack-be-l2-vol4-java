package com.loopers.application.ordering.queue;

import com.loopers.domain.ordering.queue.OrderQueueRepository;
import com.loopers.domain.ordering.queue.OrderQueueStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OrderQueueService {
    private final OrderQueueRepository orderQueueRepository;
    private final Duration tokenTtl;
    private final int admitBatchSize;
    private final Duration schedulerInterval;

    @Autowired
    public OrderQueueService(
        OrderQueueRepository orderQueueRepository,
        @Value("${commerce.workers.order-queue.token-ttl:5m}") Duration tokenTtl,
        @Value("${commerce.workers.order-queue.admit-batch-size:10}") int admitBatchSize,
        @Value("${commerce.workers.order-queue.fixed-delay-ms:1000}") long schedulerIntervalMs
    ) {
        this.orderQueueRepository = orderQueueRepository;
        this.tokenTtl = tokenTtl;
        this.admitBatchSize = admitBatchSize;
        this.schedulerInterval = Duration.ofMillis(schedulerIntervalMs);
    }

    public OrderQueueResult enter(String userId) {
        return orderQueueRepository.findToken(userId)
            .map(this::ready)
            .orElseGet(() -> waiting(orderQueueRepository.enter(userId).rank()));
    }

    public OrderQueueResult getPosition(String userId) {
        return orderQueueRepository.findToken(userId)
            .map(this::ready)
            .orElseGet(() -> orderQueueRepository.rank(userId)
                .map(this::waiting)
                .orElseGet(this::notQueued));
    }

    public Duration tokenTtl() {
        return tokenTtl;
    }

    public void requireValidToken(String userId, String token) {
        if (!orderQueueRepository.isValidToken(userId, token)) {
            throw new CoreException(ErrorType.ORDER_QUEUE_TOKEN_REQUIRED);
        }
    }

    public void deleteToken(String userId) {
        orderQueueRepository.deleteToken(userId);
    }

    private OrderQueueResult ready(String token) {
        return new OrderQueueResult(
            OrderQueueStatus.READY,
            null,
            orderQueueRepository.waitingCount(),
            0L,
            0L,
            token
        );
    }

    private OrderQueueResult waiting(long position) {
        return new OrderQueueResult(
            OrderQueueStatus.WAITING,
            position,
            orderQueueRepository.waitingCount(),
            estimateWaitSeconds(position),
            recommendPollingIntervalSeconds(position),
            null
        );
    }

    private OrderQueueResult notQueued() {
        return new OrderQueueResult(
            OrderQueueStatus.NOT_QUEUED,
            null,
            orderQueueRepository.waitingCount(),
            null,
            null,
            null
        );
    }

    private long estimateWaitSeconds(long position) {
        int batchSize = Math.max(admitBatchSize, 1);
        long intervalSeconds = Math.max(schedulerInterval.toSeconds(), 1L);
        return ((position + batchSize - 1) / batchSize) * intervalSeconds;
    }

    private long recommendPollingIntervalSeconds(long position) {
        long estimatedWaitSeconds = estimateWaitSeconds(position);
        return Math.min(Math.max(estimatedWaitSeconds, 1L), 10L);
    }
}
