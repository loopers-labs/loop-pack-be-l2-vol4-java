package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis Sorted Set 기반 대기열 구현체.
 * <p>
 * member = userId, score = 진입 시각(epoch millis)로 진입 순서를 보장한다.
 * 순번 조회(ZRANK)가 진입(ZADD) 직후의 쓰기를 반드시 봐야 하므로,
 * replica-preferred인 기본 템플릿 대신 master 템플릿만 사용한다.
 */
@Repository
public class WaitingQueueRedisRepository implements WaitingQueueRepository {

    static final String QUEUE_KEY = "waiting-queue";

    private final RedisTemplate<String, String> redisTemplate;

    public WaitingQueueRedisRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean enter(Long userId, long enteredAtMillis) {
        // ZADD NX — 이미 대기 중인 유저의 score(진입 시각)를 덮어쓰지 않아 기존 순번이 유지된다
        Boolean added = redisTemplate.opsForZSet()
            .addIfAbsent(QUEUE_KEY, String.valueOf(userId), enteredAtMillis);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> findRank(Long userId) {
        return Optional.ofNullable(
            redisTemplate.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId))
        );
    }

    @Override
    public long countWaiting() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size == null ? 0L : size;
    }

    @Override
    public void remove(Long userId) {
        redisTemplate.opsForZSet().remove(QUEUE_KEY, String.valueOf(userId));
    }

    @Override
    public List<Long> popMin(int count) {
        // ZPOPMIN — 조회와 제거를 단일 커맨드로 원자적으로 수행해, 같은 유저가 스케줄러 주기 사이에
        // 중복으로 꺼내지거나 순번 조회에서 유령 상태로 보이는 경우를 방지한다
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(QUEUE_KEY, count);
        if (popped == null) {
            return List.of();
        }
        return popped.stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .map(Long::valueOf)
            .toList();
    }
}
