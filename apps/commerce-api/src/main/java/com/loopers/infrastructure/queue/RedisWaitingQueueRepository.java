package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String WAITING_QUEUE = "waiting-queue";
    private static final String WAITING_QUEUE_SEQ = "waiting-queue:seq";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisWaitingQueueRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public QueuePosition enroll(Long userId) {
        String member = String.valueOf(userId);
        redisTemplate.opsForZSet().addIfAbsent(WAITING_QUEUE, member, nextScore());
        return positionOf(userId)
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "진입 직후 순번을 찾을 수 없습니다: userId=" + userId));
    }

    @Override
    public Optional<QueuePosition> positionOf(Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(WAITING_QUEUE, String.valueOf(userId));
        return Optional.ofNullable(rank).map(QueuePosition::of);
    }

    @Override
    public long size() {
        Long count = redisTemplate.opsForZSet().zCard(WAITING_QUEUE);
        return count == null ? 0L : count;
    }

    /**
     * 진입 순번용 단조 증가 시퀀스.
     * timestamp 대신 써서 같은 ms 동시 진입이나 서버 간 시계 오차에도 순서가 뒤섞이지 않는다.
     */
    private double nextScore() {
        Long seq = redisTemplate.opsForValue().increment(WAITING_QUEUE_SEQ);
        return seq == null ? 0d : seq;
    }
}
