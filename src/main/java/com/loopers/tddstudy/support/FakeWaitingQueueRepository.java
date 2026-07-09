package com.loopers.tddstudy.support;

import com.loopers.tddstudy.domain.queue.WaitingQueueRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeWaitingQueueRepository implements WaitingQueueRepository {

    private final Map<Long, Long> scores = new ConcurrentHashMap<>();  // userId -> timestamp

    @Override
    public boolean enqueue(Long userId, long timestamp) {
        return scores.putIfAbsent(userId, timestamp) == null;          // NX 흉내
    }

    @Override
    public Long getRank(Long userId) {
        Long my = scores.get(userId);
        if (my == null) return null;
        // 나보다 score 작은(먼저 진입한) 사람 수 = 0-based 순번
        long ahead = scores.values().stream().filter(s -> s < my).count();
        return ahead;
    }

    @Override
    public long getTotalCount() {
        return scores.size();
    }

    @Override
    public java.util.List<Long> popMin(int count) {
        java.util.List<Long> ids = scores.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByValue())
                .limit(count)
                .map(java.util.Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
        ids.forEach(scores::remove);
        return ids;
    }
}
