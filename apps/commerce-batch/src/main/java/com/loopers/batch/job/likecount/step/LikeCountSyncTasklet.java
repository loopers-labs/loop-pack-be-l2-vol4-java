package com.loopers.batch.job.likecount.step;

import com.loopers.batch.job.likecount.LikeCountSyncJobConfig;
import com.loopers.config.redis.RedisConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis에 누적된 좋아요 증감분을 product.like_count로 동기화한다.
 * <p>commerce-api는 도메인 엔티티를 별도 앱에 두므로 여기서는 네이티브 SQL(JdbcTemplate)로 갱신한다.
 * SCAN/GETDEL은 마스터 템플릿으로 수행해 복제 지연으로 갓 쓰인 증감분을 놓치지 않는다.</p>
 */
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = LikeCountSyncJobConfig.JOB_NAME)
@Component
public class LikeCountSyncTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(LikeCountSyncTasklet.class);

    /** commerce-api RedisLikeCountStore.DELTA_KEY_PREFIX 와 동일 계약. 모듈이 달라 상수를 공유하지 못해 양쪽에 박는다. */
    private static final String DELTA_KEY_PREFIX = "like:count:delta:";
    private static final int SCAN_COUNT = 1000;

    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    public LikeCountSyncTasklet(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        JdbcTemplate jdbcTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int applied = 0;
        int failed = 0;
        ScanOptions options = ScanOptions.scanOptions().match(DELTA_KEY_PREFIX + "*").count(SCAN_COUNT).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Long productId = parseProductId(key);
                if (productId == null) {
                    continue;
                }
                // GETDEL: 증감분을 원자적으로 claim. 이후 도착하는 INCRBY는 새 키로 누적되어 다음 실행에 반영된다(유실 없음).
                String raw = redisTemplate.opsForValue().getAndDelete(key);
                if (raw == null) {
                    continue;
                }
                long delta = Long.parseLong(raw);
                if (delta == 0) {
                    continue;
                }
                try {
                    jdbcTemplate.update(
                        "UPDATE product SET like_count = GREATEST(0, like_count + ?) WHERE id = ? AND deleted_at IS NULL",
                        delta, productId
                    );
                    applied++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("좋아요 수 동기화 실패 productId={}, delta={}", productId, delta, e);
                }
            }
        }
        log.info("좋아요 수 배치 동기화 완료 applied={}, failed={}", applied, failed);
        return RepeatStatus.FINISHED;
    }

    private Long parseProductId(String key) {
        try {
            return Long.parseLong(key.substring(DELTA_KEY_PREFIX.length()));
        } catch (NumberFormatException e) {
            log.warn("좋아요 증감분 키 파싱 실패 key={}", key, e);
            return null;
        }
    }
}
