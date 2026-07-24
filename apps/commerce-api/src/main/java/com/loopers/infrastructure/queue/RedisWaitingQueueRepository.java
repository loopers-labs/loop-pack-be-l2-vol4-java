package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String KEY = "waiting-queue";

    // 순번 정확성이 핵심이라 복제 지연이 없는 master 템플릿으로 읽기까지 통일한다.
    private final ZSetOperations<String, String> zSet;

    public RedisWaitingQueueRepository(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate
    ) {
        this.zSet = redisTemplate.opsForZSet();
    }

    @Override
    public boolean add(Long userId, double score) {
        // ZADD NX: 이미 있는 member면 score를 덮어쓰지 않아 기존 순번이 유지된다.
        Boolean added = zSet.addIfAbsent(KEY, member(userId), score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> rank(Long userId) {
        return Optional.ofNullable(zSet.rank(KEY, member(userId)));
    }

    @Override
    public long size() {
        Long size = zSet.zCard(KEY);
        return size == null ? 0L : size;
    }

    @Override
    public List<Long> pollFront(long count) {
        if (count <= 0) {
            return List.of();
        }
        // ZPOPMIN: score가 낮은(먼저 진입한) count개를 원자적으로 제거+반환.
        // 반환 Set은 LinkedHashSet으로 score 오름차순(진입 순서)을 보존한다.
        Set<ZSetOperations.TypedTuple<String>> popped = zSet.popMin(KEY, count);
        if (popped == null || popped.isEmpty()) {
            return List.of();
        }
        return popped.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(Objects::nonNull)
            .map(Long::valueOf)
            .toList();
    }

    private String member(Long userId) {
        return String.valueOf(userId);
    }
}
