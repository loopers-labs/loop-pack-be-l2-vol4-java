package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@RequiredArgsConstructor
@Component
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;

    public Long enter(Long userId) {
        long timestampMillis = Instant.now().toEpochMilli();
        return waitingQueueRepository.enter(userId, timestampMillis);
    }

    public Long getRank(Long userId) {
        return waitingQueueRepository.rank(userId);
    }

    public Long size() {
        return waitingQueueRepository.size();
    }
}
