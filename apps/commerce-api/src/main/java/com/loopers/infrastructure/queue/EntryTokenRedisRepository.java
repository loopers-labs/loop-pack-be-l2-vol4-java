package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.EntryTokenRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class EntryTokenRedisRepository implements EntryTokenRepository {

    // OrderQueueRedisRepository.QUEUE_KEY 와 반드시 동일해야 한다(같은 대기열을 가리킴).
    private static final String QUEUE_KEY = "waiting-queue";
    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    // ZPOPMIN + 토큰 SET 을 원자화한 스크립트. 접두사는 ARGV 로 넘겨 자바 상수와 단일 출처를 유지한다.
    private static final RedisScript<List> DRAIN_SCRIPT =
        RedisScript.of(new ClassPathResource("scripts/queue-drain.lua"), List.class);

    private final RedisTemplate<String, String> redisTemplate;

    // 대기열 쓰기(ZPOPMIN)·토큰 발급은 반드시 master 에서. (OrderQueueRedisRepository 와 동일 이유)
    public EntryTokenRedisRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> issueToNext(int count, Duration ttl) {
        if (count <= 0) {
            return List.of();
        }
        // ARGV = [count, ttlSeconds, prefix, token1, token2, ... tokenN]. 실제 꺼낸 수만큼만 스크립트가 소비한다.
        List<String> args = new ArrayList<>(3 + count);
        args.add(String.valueOf(count));
        args.add(String.valueOf(ttl.toSeconds()));
        args.add(TOKEN_KEY_PREFIX);
        for (int i = 0; i < count; i++) {
            args.add(UUID.randomUUID().toString());
        }

        List<String> issued = redisTemplate.execute(DRAIN_SCRIPT, List.of(QUEUE_KEY), args.toArray());
        if (issued == null) {
            return List.of();
        }
        return issued.stream().map(Long::valueOf).toList();
    }

    @Override
    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId));
    }
}
