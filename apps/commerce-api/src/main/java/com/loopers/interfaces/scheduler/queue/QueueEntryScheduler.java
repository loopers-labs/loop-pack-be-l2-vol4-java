package com.loopers.interfaces.scheduler.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueThroughputPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueEntryScheduler {

    private final QueueRepository queueRepository;
    private final EntryTokenRepository entryTokenRepository;

    @Value("${queue.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Scheduled(fixedRate = QueueThroughputPolicy.SCHEDULER_INTERVAL_MS)
    public void scheduledIssueTokens() {
        if (schedulerEnabled) {
            issueTokens();
        }
    }

    public void issueTokens() {
        List<Long> userIds = queueRepository.popMin(QueueThroughputPolicy.BATCH_SIZE);
        for (Long userId : userIds) {
            entryTokenRepository.issue(userId);
        }
        if (!userIds.isEmpty()) {
            log.info("[QueueEntryScheduler] 토큰 발급 완료: count={}", userIds.size());
        }
    }
}
