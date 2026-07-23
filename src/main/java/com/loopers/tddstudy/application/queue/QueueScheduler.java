package com.loopers.tddstudy.application.queue;

import com.loopers.tddstudy.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QueueScheduler {

    private final WaitingQueueRepository queueRepository;
    private final EntryTokenService entryTokenService;
    private final int batchSize;

    public QueueScheduler(WaitingQueueRepository queueRepository,
                          EntryTokenService entryTokenService,
                          @Value("${queue.batch-size:175}") int batchSize) {
        this.queueRepository = queueRepository;
        this.entryTokenService = entryTokenService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelay = 1000)   // 1초마다 batchSize명 입장 (Thundering Herd 완화는 Nice-to-Have)
    public void admit() {
        List<Long> admitted = queueRepository.popMin(batchSize);
        for (Long userId : admitted) {
            entryTokenService.issue(userId);
        }
    }
}
