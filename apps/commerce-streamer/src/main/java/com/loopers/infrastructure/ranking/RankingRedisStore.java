package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingContribution;
import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 실시간 상품 랭킹 ZSET 저장소 (쓰기 측 — streamer).
 *
 * <p>배치 컨슈머가 집계한 상품별 점수 재료를 가중치와 곱해 일간/시간별 ZSET 에 {@code ZINCRBY} 로 누적한다.
 * 가중치({@code ranking:weights})는 배치마다 Redis 에서 <strong>새로 읽는다</strong> — 캐싱하지 않아
 * 운영 중 가중치를 바꾸면 다음 배치부터 즉시 반영된다.
 *
 * <p>템플릿은 <strong>master</strong> 를 사용한다 — 쓰기 직후 {@code needsTtl}(getExpire) 같은
 * read-your-write 가 있어, replica 읽기면 복제 지연만큼 TTL 이 재설정(슬라이딩)될 수 있다.
 */
@Slf4j
@Component
public class RankingRedisStore {

    public static final String ALL_KEY_PREFIX = "ranking:all:";
    public static final String HOUR_KEY_PREFIX = "ranking:hour:";
    public static final String WEIGHTS_KEY = "ranking:weights";

    public static final String WEIGHT_VIEW = "view";
    public static final String WEIGHT_LIKE = "like";
    public static final String WEIGHT_ORDER = "order";

    private static final double DEFAULT_WEIGHT_VIEW = 0.1;
    private static final double DEFAULT_WEIGHT_LIKE = 0.2;
    private static final double DEFAULT_WEIGHT_ORDER = 0.6;

    private static final Duration ALL_TTL = Duration.ofHours(48);
    private static final Duration HOUR_TTL = Duration.ofHours(3);
    /** 콜드스타트 이월 비율 — 자정 직전 다음 날 키를 오늘 점수의 10% 로 시딩한다. */
    private static final double CARRY_OVER_RATIO = 0.1;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisStore(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 배치 집계 반영 — 가중치를 읽어 상품별 최종 점수를 계산하고, 일간/시간별 키에 파이프라인 한 번으로 ZINCRBY 한다.
     *
     * <p>Redis 장애로 랭킹 반영이 실패해도 예외를 던지지 않는다(fail-open) — DB 집계(product_metrics)가
     * 정합성의 원천이고, 랭킹은 근사 뷰다. 여기서 던지면 배치가 재전달되지만 멱등 가드에 의해 집계가 비어
     * 결국 다시 반영되지 못하므로, 실패를 삼키고 로그만 남기는 편이 낫다.
     */
    public void incrementScores(Map<Long, RankingContribution> contributions) {
        if (contributions.isEmpty()) {
            return;
        }
        try {
            double wView = weight(WEIGHT_VIEW, DEFAULT_WEIGHT_VIEW);
            double wLike = weight(WEIGHT_LIKE, DEFAULT_WEIGHT_LIKE);
            double wOrder = weight(WEIGHT_ORDER, DEFAULT_WEIGHT_ORDER);

            String allKey = ALL_KEY_PREFIX + LocalDate.now().format(DAY_FMT);
            String hourKey = HOUR_KEY_PREFIX + LocalDateTime.now().format(HOUR_FMT);
            // TTL 은 그 키의 "최초 쓰기 시점"에 한 번만 건다 — 매 배치 재설정(슬라이딩)하면
            // 마지막 갱신 시각 기준으로 계속 늘어나 스펙(TTL: 2Day)을 초과해 살아있게 된다.
            boolean allNeedsTtl = needsTtl(allKey);
            boolean hourNeedsTtl = needsTtl(hourKey);

            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public Object execute(RedisOperations operations) {
                    for (Map.Entry<Long, RankingContribution> entry : contributions.entrySet()) {
                        RankingContribution c = entry.getValue();
                        double score = wView * c.viewCount() + wLike * c.likeDelta() + wOrder * c.orderScore();
                        if (score == 0.0) {
                            continue;   // 기여가 상쇄되어 0 이면 ZSET 을 건드리지 않는다
                        }
                        String member = String.valueOf(entry.getKey());
                        operations.opsForZSet().incrementScore(allKey, member, score);
                        operations.opsForZSet().incrementScore(hourKey, member, score);
                    }
                    if (allNeedsTtl) {
                        operations.expire(allKey, ALL_TTL);
                    }
                    if (hourNeedsTtl) {
                        operations.expire(hourKey, HOUR_TTL);
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("[Ranking] ZSET 반영 실패 — 무시(DB 집계는 정상). size={}", contributions.size(), e);
        }
    }

    /**
     * 콜드스타트 이월 — 다음 날 키를 오늘 점수의 {@value #CARRY_OVER_RATIO} 배로 시딩한다(ZUNIONSTORE).
     * ZUNIONSTORE 는 TTL 없는 새 키를 만들므로 48시간 TTL 을 별도로 건다.
     */
    public void carryOver(LocalDate today, LocalDate tomorrow) {
        String todayKey = ALL_KEY_PREFIX + today.format(DAY_FMT);
        String tomorrowKey = ALL_KEY_PREFIX + tomorrow.format(DAY_FMT);
        try {
            redisTemplate.opsForZSet().unionAndStore(
                todayKey, List.of(), tomorrowKey, Aggregate.SUM, Weights.of(CARRY_OVER_RATIO));
            redisTemplate.expire(tomorrowKey, ALL_TTL);
        } catch (Exception e) {
            log.warn("[Ranking] 콜드스타트 이월 실패 — 무시. today={}, tomorrow={}", todayKey, tomorrowKey, e);
        }
    }

    /** 키에 TTL 이 아직 안 걸려있는지(최초 쓰기인지) 확인한다. -1(무기한)/-2(키 없음) 이면 아직 안 건 것으로 본다. */
    private boolean needsTtl(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return ttl == null || ttl < 0;
    }

    /** 가중치 필드를 읽고, 없으면 기본값을 시딩(HSETNX)한 뒤 그 값을 사용한다. 배치마다 새로 읽어 캐싱하지 않는다. */
    private double weight(String field, double defaultValue) {
        Object raw = redisTemplate.opsForHash().get(WEIGHTS_KEY, field);
        if (raw == null) {
            redisTemplate.opsForHash().putIfAbsent(WEIGHTS_KEY, field, String.valueOf(defaultValue));
            return defaultValue;
        }
        return Double.parseDouble(raw.toString());
    }
}
