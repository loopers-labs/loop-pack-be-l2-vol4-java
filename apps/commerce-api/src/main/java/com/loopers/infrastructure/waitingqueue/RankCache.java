package com.loopers.infrastructure.waitingqueue;

import com.loopers.application.waitingqueue.RankView;
import com.loopers.config.waitingqueue.WaitingQueueProperties;
import com.loopers.domain.waitingqueue.QueueStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 순번 조회 결과 단기 캐시(D5·NFR-7). 대량 폴링이 Redis ZSET 조회에 도달하지 않도록 흡수한다.
 * rank는 근사값이어도 UX 무해 → 기본(REPLICA_PREFERRED) 템플릿으로 읽어도 된다.
 * 저장 포맷은 파이프 구분 문자열(status|rank|ahead|eta|poll) — 역직렬화 취약점 없이 가볍게.
 */
@Component
public class RankCache {

    private static final String PREFIX = "rank:cache:";
    private static final String DELIM = "\\|";

    private final ValueOperations<String, String> value;
    private final Duration ttl;

    public RankCache(RedisTemplate<String, String> defaultRedisTemplate, WaitingQueueProperties properties) {
        this.value = defaultRedisTemplate.opsForValue();
        this.ttl = Duration.ofSeconds(properties.rankCacheSeconds());
    }

    public Optional<RankView> get(Long userId) {
        String raw = value.get(PREFIX + userId);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(decode(raw));
    }

    public void put(Long userId, RankView view) {
        value.set(PREFIX + userId, encode(view), ttl);
    }

    private String encode(RankView v) {
        return v.status().name()
            + "|" + (v.rank() == null ? "" : v.rank())
            + "|" + v.aheadCount()
            + "|" + v.estimatedWaitSeconds()
            + "|" + v.pollAfterSeconds();
    }

    private RankView decode(String raw) {
        String[] p = raw.split(DELIM, -1);
        Long rank = p[1].isEmpty() ? null : Long.valueOf(p[1]);
        return new RankView(
            QueueStatus.valueOf(p[0]),
            rank,
            Long.parseLong(p[2]),
            Long.parseLong(p[3]),
            Integer.parseInt(p[4])
        );
    }
}
