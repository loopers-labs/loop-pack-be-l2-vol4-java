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
 * 집계 전에 해당 기간의 staging 을 비운다(Step 1/3).
 *
 * <p><b>왜 별도 Step 인가</b>: staging Writer 의 UPSERT 만으로는 부족하다. 지난 실행에는 있었지만 이번
 * 재집계에서 스코어가 0 이하로 떨어져 필터된 상품이 <b>옛 스코어 그대로 남아</b> 순위에 끼어든다.
 * 재실행이 항상 같은 결과로 수렴하려면(멱등) 쓰기 전에 지워야 한다.
 */
@Slf4j
@RequiredArgsConstructor
public class PeriodStagingCleanupTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final RankingPeriodType periodType;
    private final PeriodRange range;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM product_rank_staging WHERE period_type = ? AND period_start = ?",
                periodType.code(), Date.valueOf(range.start()));
        log.info("기간 랭킹 staging 정리: type={}, period={}~{}, 삭제={}건",
                periodType.code(), range.start(), range.end(), deleted);
        return RepeatStatus.FINISHED;
    }
}
