package com.loopers.infrastructure.waitingqueue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.waitingqueue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

/**
 * 대기 순서(waiting:queue ZSET) 어댑터. 순서·중복체크는 복제 지연을 허용하면 안 되므로
 * 마스터 템플릿({@link RedisConfig#REDIS_TEMPLATE_MASTER})으로만 조작한다(04 §0).
 * (발급 배치의 pop은 원자성을 위해 {@link RedisTokenIssuer}의 Lua 스크립트에서 수행한다.)
 */
@Repository
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String QUEUE_KEY = "waiting:queue";
    private static final String SEQ_KEY = "waiting:seq";

    private final ZSetOperations<String, String> zset;
    private final ValueOperations<String, String> value;

    public RedisWaitingQueueRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.zset = masterRedisTemplate.opsForZSet();
        this.value = masterRedisTemplate.opsForValue();
    }

    @Override
    public boolean enqueueIfAbsent(Long userId) {
        String member = userId.toString();
        if (zset.score(QUEUE_KEY, member) != null) {
            return false;
        }
        long seq = nextSeq();
        Boolean added = zset.addIfAbsent(QUEUE_KEY, member, (double) seq);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long rank(Long userId) {
        return zset.rank(QUEUE_KEY, userId.toString());
    }

    @Override
    public boolean isQueued(Long userId) {
        return zset.score(QUEUE_KEY, userId.toString()) != null;
    }

    @Override
    public long size() {
        Long size = zset.zCard(QUEUE_KEY);
        return size == null ? 0L : size;
    }

    private long nextSeq() {
        Long seq = value.increment(SEQ_KEY);
        return seq == null ? System.nanoTime() : seq;
    }
}
