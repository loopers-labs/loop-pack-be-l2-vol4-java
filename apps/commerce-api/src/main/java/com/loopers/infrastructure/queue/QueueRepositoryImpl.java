package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class QueueRepositoryImpl implements QueueRepository {

    private static final String QUEUE_KEY = "waiting-queue";

    private final RedisTemplate<String, String> redisTemplate;

    public QueueRepositoryImpl(@Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long enter(Long userId, long timestampMillis) {
        redisTemplate.opsForZSet().addIfAbsent(QUEUE_KEY, String.valueOf(userId), timestampMillis);
        return rank(userId)
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "대기열 진입에 실패했습니다."));
    }

    @Override
    public Optional<Long> rank(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId)));
    }

    @Override
    public long size() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size == null ? 0L : size;
    }

    @Override
    public List<Long> popMin(int count) {
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
        if (popped == null) {
            return List.of();
        }
        return popped.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(Objects::nonNull)
            .map(Long::valueOf)
            .toList();
    }
}
