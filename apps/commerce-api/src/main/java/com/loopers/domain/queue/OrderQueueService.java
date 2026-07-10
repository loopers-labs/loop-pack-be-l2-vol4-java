package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class OrderQueueService {

    private final OrderQueueRepository orderQueueRepository;

    /** 대기열에 진입시키고 현재 순번(1-based)을 반환한다. */
    public long enter(Long userId) {
        orderQueueRepository.enter(userId, System.currentTimeMillis());
        return position(userId);
    }

    /** 현재 순번(1-based). 대기열에 없으면 0(입장 완료 또는 미진입). */
    public long position(Long userId) {
        return orderQueueRepository.findRank(userId)
            .map(rank -> rank + 1)
            .orElse(0L);
    }

    /** 전체 대기 인원. */
    public long totalWaiting() {
        return orderQueueRepository.size();
    }
}
