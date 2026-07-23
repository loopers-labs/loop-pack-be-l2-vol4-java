package com.loopers.batch.job.ranking.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 확정된 하루치 랭킹 ZSET 을 ranking_daily_snapshot 으로 내린다. ZSET 은 TTL 2일이라 그 뒤엔 사라지므로,
 * 이 배치가 돌지 않으면 과거 랭킹은 영영 조회할 수 없다.
 *
 * <p><b>대상 일자</b>: job parameter {@code snapshotDate}(yyyyMMdd), 없으면 <b>어제</b>(KST). 오늘을 찍지 않는 이유는
 * 오늘 랭킹이 아직 계속 변하기 때문 — 확정된 날짜만 스냅샷한다. 어제 키는 TTL(2일) 안이라 자정 직후에도 살아있다.
 *
 * <p><b>상위 N 만 적재</b>({@code ranking.snapshot.top-n}, 기본 100). 과거 랭킹을 500위까지 되짚는 수요는 없고,
 * 전량 적재는 저장 비용만 늘린다. 이 한계 때문에 스냅샷에서 읽는 과거 날짜의 totalCount 는 최대 N 이다.
 *
 * <p><b>재실행 안전(멱등)</b>: 해당 일자를 지우고 다시 넣는다(delete-then-insert). 같은 날짜로 몇 번을 돌려도
 * 결과가 같다 — 배치 재시도/수동 재실행에 안전하다.
 *
 * <p>commerce-batch 는 JPA 엔티티가 없으므로(앱 경계) {@code JdbcTemplate} 로 직접 쓴다 — 스키마 정의와 읽기는
 * commerce-api 의 {@code RankingSnapshotEntity} 가 갖는다(product_metrics 를 streamer 가 쓰는 방식과 동일).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingSnapshotTasklet implements Tasklet {

    /** commerce-api / commerce-streamer 의 RankingKey 와 동일 포맷이어야 한다 — 앱 경계라 문자열이 유일한 계약. */
    private static final String KEY_PREFIX = "ranking:all:";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int INSERT_BATCH_SIZE = 500;

    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Value("${ranking.snapshot.top-n:100}")
    private int topN;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate date = resolveDate(chunkContext);
        String key = KEY_PREFIX + date.format(YYYYMMDD);

        Set<TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1L);

        if (tuples == null || tuples.isEmpty()) {
            // 그날 활동이 없었거나 이미 TTL 이 지나 사라졌다. 지우고 끝낸다(빈 날짜로 확정).
            int deleted = jdbcTemplate.update("DELETE FROM ranking_daily_snapshot WHERE ranking_date = ?", date);
            log.info("랭킹 스냅샷: date={} 대상 없음(ZSET 비어있음) — 기존 {}건 정리", date, deleted);
            return RepeatStatus.FINISHED;
        }

        List<Object[]> rows = new ArrayList<>(tuples.size());
        int rank = 1;
        for (TypedTuple<String> tuple : tuples) { // reverseRange 는 내림차순 순서를 보존한다
            String member = tuple.getValue();
            Double score = tuple.getScore();
            if (member == null) {
                continue;
            }
            rows.add(new Object[]{date, Long.valueOf(member), rank++, score == null ? 0.0 : score});
        }

        // 멱등: 해당 일자를 지우고 다시 넣는다. 같은 트랜잭션이 아니어도 재실행하면 같은 상태로 수렴한다.
        jdbcTemplate.update("DELETE FROM ranking_daily_snapshot WHERE ranking_date = ?", date);
        for (int i = 0; i < rows.size(); i += INSERT_BATCH_SIZE) {
            List<Object[]> chunk = rows.subList(i, Math.min(i + INSERT_BATCH_SIZE, rows.size()));
            jdbcTemplate.batchUpdate(
                    "INSERT INTO ranking_daily_snapshot (ranking_date, product_id, rank_no, score, created_at) "
                            + "VALUES (?, ?, ?, ?, now())", chunk);
        }

        log.info("랭킹 스냅샷: date={}, key={}, 적재={}건(topN={})", date, key, rows.size(), topN);
        contribution.incrementWriteCount(rows.size());
        return RepeatStatus.FINISHED;
    }

    /** job parameter {@code snapshotDate}(yyyyMMdd) 우선, 없으면 어제(KST). */
    private LocalDate resolveDate(ChunkContext chunkContext) {
        Object raw = chunkContext.getStepContext().getJobParameters().get("snapshotDate");
        if (raw == null) {
            return LocalDate.now(KST).minusDays(1);
        }
        return LocalDate.parse(raw.toString(), YYYYMMDD);
    }
}
