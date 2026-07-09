package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * ZSET 기반 대기열 (score = 진입 시각 ms, member = userId).
 *
 * <p>score 순 정렬이 곧 진입 순서고, member 가 Set 이라 중복 진입이 자연 방지된다.
 * 진입은 {@code ZADD NX}(addIfAbsent) — 재진입 시 score 를 덮어쓰면 순번이 뒤로 리셋되기 때문.</p>
 *
 * <p><b>같은 ms 진입자의 순서</b>는 member(userId) 사전순 tie-break 으로 정해진다 — 즉 밀리초
 * 단위까지는 FIFO 지만, 같은 ms 안에서는 도착 순서가 아니라 userId 순이다. 왜곡 창은 ≤1ms 로,
 * 초~분 단위로 대기하는 유저에게 체감 불가능하고 특정 유저에게 지속 이득도 없다(어느 ms 에 착지할지는
 * 통제 불가). 엄격한 실시간 FIFO 가 필요하면 {@code INCR} 단조 시퀀스를 score 로 쓰되, 재진입 멱등을
 * 지키려면 "존재 시 기존 score 유지, 부재 시 INCR 후 ZADD"를 Lua 로 원자화해야 한다 — ZADD NX 한
 * 명령의 단순함을 감지 불가능한 공정성과 맞바꾸는 셈이라, 현재는 ms score 를 유지한다.</p>
 *
 * <p>{@code masterRedisTemplate} 을 쓰는 이유: 기본 템플릿은 REPLICA_PREFERRED 라 ZADD 직후
 * ZRANK 가 replica lag 로 "대기열에 없음"을 반환할 수 있다. 대기열 연산은 건당 O(log N) 단건이라
 * master 집중 부담이 없고, 읽기 분산의 이득보다 정합성이 우선이다.</p>
 *
 * <p>캐시({@code ProductCacheRepositoryImpl})와 달리 예외를 삼키지 않는다 — 대기열은 우회 가능한
 * 부가 기능이 아니라 순번의 진실 원본이므로, Redis 장애는 그대로 실패로 드러나야 한다.</p>
 */
@Component
public class WaitingQueueRedisRepository implements WaitingQueueRepository {

    private static final String KEY = "waiting-queue";

    private final RedisTemplate<String, String> redisTemplate;

    public WaitingQueueRedisRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean enter(Long userId, long enteredAtMillis) {
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(KEY, userId.toString(), enteredAtMillis);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> rank(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForZSet().rank(KEY, userId.toString()));
    }

    @Override
    public long size() {
        Long size = redisTemplate.opsForZSet().zCard(KEY);
        return size == null ? 0L : size;
    }

    @Override
    public List<Long> popFront(int count) {
        Set<TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(KEY, count);
        if (popped == null) {
            return List.of();
        }
        return popped.stream()
                .map(TypedTuple::getValue)
                .filter(Objects::nonNull)
                .map(Long::valueOf)
                .toList();
    }
}
