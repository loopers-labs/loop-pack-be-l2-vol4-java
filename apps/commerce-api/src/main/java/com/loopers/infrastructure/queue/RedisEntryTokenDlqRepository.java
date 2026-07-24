package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.EntryTokenDlqRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisEntryTokenDlqRepository implements EntryTokenDlqRepository {

    private static final String KEY = "entry-token:dlq";

    private final ListOperations<String, String> ops;

    public RedisEntryTokenDlqRepository(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate
    ) {
        this.ops = redisTemplate.opsForList();
    }

    @Override
    public void push(Long userId) {
        // RPUSH: 실패함 뒤에 추가(FIFO 유지).
        ops.rightPush(KEY, String.valueOf(userId));
    }

    @Override
    public List<Long> drain(long count) {
        if (count <= 0) {
            return List.of();
        }
        // LPOP key count: 앞에서 최대 count개를 원자적으로 제거+반환.
        List<String> popped = ops.leftPop(KEY, count);
        if (popped == null || popped.isEmpty()) {
            return List.of();
        }
        return popped.stream()
            .map(Long::valueOf)
            .toList();
    }
}
