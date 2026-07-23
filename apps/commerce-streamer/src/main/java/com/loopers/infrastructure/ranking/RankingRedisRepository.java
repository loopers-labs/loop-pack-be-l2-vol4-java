package com.loopers.infrastructure.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 실시간 랭킹 ZSET 쓰기 어댑터. 상품별 점수 델타를 {@code ZINCRBY} 로 누적하고 키에 TTL 을 건다.
 *
 * <p><b>파이프라인 배치 쓰기</b>: 배치 리스너가 한 번에 넘긴 상품별 합산 델타를 파이프라인으로 묶어
 * 왕복(RTT)을 줄인다. 쓰기는 master-replica 구성에서 Lettuce 가 자동으로 master 로 라우팅한다.
 *
 * <p><b>TTL</b>: 매 배치마다 {@code expire(key, 2d)} 로 갱신한다 — 일간 키라 그 날 마지막 쓰기 기준 2일 뒤 만료되어
 * "오늘/어제" 랭킹 조회가 항상 유효하다.
 *
 * <p><b>정합성</b>: {@code ZINCRBY} 는 그 자체로 멱등이 아니므로, 같은 이벤트를 두 번 반영하지 않는 책임은
 * 호출자({@link com.loopers.application.ranking.RankingAggregator})의 event_handled 필터에 있다. 이 어댑터는
 * 넘겨받은 델타를 그대로 가산할 뿐이다 — 중복 제거된 fresh 이벤트만 넘어온다고 가정한다.
 */
@Component
@RequiredArgsConstructor
public class RankingRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    /** {@code key -> (productId -> scoreDelta)} 를 파이프라인 ZINCRBY 로 반영하고 각 키에 TTL 을 건다. */
    public void incrementAll(Map<String, Map<String, Double>> deltasByKey) {
        if (deltasByKey == null || deltasByKey.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                for (Map.Entry<String, Map<String, Double>> keyEntry : deltasByKey.entrySet()) {
                    String key = keyEntry.getKey();
                    for (Map.Entry<String, Double> member : keyEntry.getValue().entrySet()) {
                        double delta = member.getValue();
                        if (delta == 0.0) {
                            continue; // 상쇄되어 0이 된 델타는 쓰기 불필요
                        }
                        ops.opsForZSet().incrementScore(key, member.getKey(), delta);
                    }
                    ops.expire(key, RankingKey.TTL);
                }
                return null;
            }
        });
    }
}
