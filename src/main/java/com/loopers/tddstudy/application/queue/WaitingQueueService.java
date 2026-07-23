package com.loopers.tddstudy.application.queue;

import com.loopers.tddstudy.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WaitingQueueService {

    private final WaitingQueueRepository queueRepository;
    private final EntryTokenService entryTokenService;
    private final int throughputPerSecond;   // 처리량 기준

    public WaitingQueueService(WaitingQueueRepository queueRepository,
                               EntryTokenService entryTokenService,
                               @Value("${queue.throughput-per-second:175}") int throughputPerSecond) {
        this.queueRepository = queueRepository;
        this.entryTokenService = entryTokenService;
        this.throughputPerSecond = throughputPerSecond;
    }

    public QueueStatus enter(Long userId) {
        queueRepository.enqueue(userId, System.currentTimeMillis());  // NX: 재진입 순번 보존
        return position(userId);
    }

    public QueueStatus position(Long userId) {
        String token = entryTokenService.getToken(userId);
        if (token != null) {
            // 이미 입장 대상 → 순번 0 + 토큰 (폴링 한 번으로 입장)
            return new QueueStatus(false, 0, 0, queueRepository.getTotalCount(), token);
        }
        Long rank = queueRepository.getRank(userId);
        long total = queueRepository.getTotalCount();
        if (rank == null) {
            return QueueStatus.notInQueue(total);
        }
        return new QueueStatus(true, rank + 1, rank / throughputPerSecond, total, null);
    }
}
