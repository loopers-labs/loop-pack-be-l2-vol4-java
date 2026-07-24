package com.loopers.batch.job.ranking.period.step;

import com.loopers.batch.job.ranking.period.PeriodRange;
import com.loopers.batch.job.ranking.period.RankingPeriodType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;

/**
 * staging 의 스코어를 전역 정렬해 상위 N 의 순위를 확정하고 MV 로 옮긴다(Step 3/3).
 *
 * <p><b>왜 청크가 아니라 Tasklet 인가</b>: 순위는 전 상품을 다 봐야 정해지는 <b>전역</b> 연산이다.
 * 청크는 스트리밍이라 "지금 이 아이템이 몇 위인지"를 알 수 없다. 정렬·순번은 DB 가 가장 잘하는 일이므로
 * {@code ROW_NUMBER()} 한 문장에 맡기고, 애플리케이션은 전량을 메모리로 올리지 않는다.
 *
 * <p><b>멱등</b>: 해당 기간을 지우고 다시 넣는다(delete-then-insert). 같은 기간으로 몇 번을 돌려도 결과가 같다.
 * Step 전체가 한 트랜잭션이라 중간 상태(지워졌지만 안 채워진 MV)가 외부에 보이지 않는다.
 *
 * <p><b>tie-break 는 {@code product_id DESC}</b> — 상품 리스팅의 키셋 페이지네이션과 같은 규약이라
 * 동점일 때 순서가 흔들리지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class PeriodRankConfirmTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final RankingPeriodType periodType;
    private final PeriodRange range;
    private final int topN;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String mvTable = periodType.mvTable(); // enum 이 주는 고정 문자열 — 외부 입력이 아니다

        jdbcTemplate.update("DELETE FROM " + mvTable + " WHERE period_start = ?", Date.valueOf(range.start()));

        int inserted = jdbcTemplate.update("""
                        INSERT INTO %s (period_start, product_id, period_end, rank_no, score, created_at)
                        SELECT ?, product_id, ?,
                               ROW_NUMBER() OVER (ORDER BY score DESC, product_id DESC),
                               score,
                               now()
                          FROM product_rank_staging
                         WHERE period_type = ? AND period_start = ?
                         ORDER BY score DESC, product_id DESC
                         LIMIT %d
                        """.formatted(mvTable, topN),
                Date.valueOf(range.start()), Date.valueOf(range.end()),
                periodType.code(), Date.valueOf(range.start()));

        // staging 은 중간 산출물이라 남길 이유가 없다. 다음 실행이 지우기 전에 여기서 회수한다.
        jdbcTemplate.update("DELETE FROM product_rank_staging WHERE period_type = ? AND period_start = ?",
                periodType.code(), Date.valueOf(range.start()));

        log.info("기간 랭킹 확정: type={}, period={}~{}, 적재={}건(topN={})",
                periodType.code(), range.start(), range.end(), inserted, topN);
        contribution.incrementWriteCount(inserted);
        return RepeatStatus.FINISHED;
    }
}
