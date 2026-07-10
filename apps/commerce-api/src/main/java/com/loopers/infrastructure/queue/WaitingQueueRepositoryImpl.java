package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 대기열 Redis 어댑터 — ZSET(score=진입 epoch millis, member=loginId).
 * ZRANK 직후 일관성을 위해 조회 포함 전 연산을 master 템플릿으로 고정한다(REPLICA_PREFERRED 금지).
 * 캐시(ProductCacheStore)와 달리 대기열은 원본 데이터라 Redis 예외를 삼키지 않고 전파한다.
 * (장애 시 게이트는 fail-open, 대기열 API 는 5xx 전파 — 스펙 Graceful Degradation 정책.)
 */
@Component
public class WaitingQueueRepositoryImpl implements WaitingQueueRepository {

    private static final String KEY = "queue:order:waiting";

    private final RedisTemplate<String, String> redisTemplate;

    public WaitingQueueRepositoryImpl(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean enqueue(String loginId, long enteredAtMillis) {
        // ZADD NX — 이미 대기 중이면 score 를 갱신하지 않는다(재진입해도 줄 뒤로 밀리지 않음).
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(KEY, loginId, enteredAtMillis);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> findRank(String loginId) {
        return Optional.ofNullable(redisTemplate.opsForZSet().rank(KEY, loginId));
    }

    @Override
    public long countWaiting() {
        Long count = redisTemplate.opsForZSet().zCard(KEY);
        return count != null ? count : 0L;
    }

    @Override
    public List<String> peekNextBatch(int count) {
        if (count <= 0) {
            return List.of(); // ZRANGE 0 -1 은 "전체"라는 함정 방어 — 음수/0 요청은 빈 배치
        }
        Set<String> peeked = redisTemplate.opsForZSet().range(KEY, 0, count - 1L);
        if (peeked == null || peeked.isEmpty()) {
            return List.of();
        }
        return List.copyOf(peeked); // LinkedHashSet — score 오름차순 유지
    }

    @Override
    public void remove(List<String> loginIds) {
        if (loginIds.isEmpty()) {
            return;
        }
        redisTemplate.opsForZSet().remove(KEY, loginIds.toArray());
    }
}
