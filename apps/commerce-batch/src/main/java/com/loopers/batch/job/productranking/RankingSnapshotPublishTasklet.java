package com.loopers.batch.job.productranking;

import java.sql.Date;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@RequiredArgsConstructor
public class RankingSnapshotPublishTasklet implements Tasklet {

  private final JdbcTemplate jdbcTemplate;
  private final long jobInstanceId;
  private final ProductRankingPeriod period;
  private final LocalDate targetDate;

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    LocalDate periodStartDate = period.startDate(targetDate);
    String table = period.materializedViewTable();

    jdbcTemplate.update(
        "DELETE FROM " + table + " WHERE aggregation_date = ?", Date.valueOf(targetDate));

    int insertedRows =
        jdbcTemplate.update(
            """
INSERT INTO %s (
    aggregation_date,
    period_start_date,
    period_end_date,
    rank_position,
    product_id,
    score,
    generated_at
)
SELECT ?, ?, ?, ranked.rank_position, ranked.product_id, ranked.score, CURRENT_TIMESTAMP(6)
FROM (
    SELECT
        product_id,
        score,
        ROW_NUMBER() OVER (ORDER BY score DESC, product_id ASC) AS rank_position
    FROM stg_product_rank_aggregation
    WHERE job_instance_id = ? AND period_type = ?
) ranked
WHERE ranked.rank_position <= 100
ORDER BY ranked.rank_position
"""
                .formatted(table),
            Date.valueOf(targetDate),
            Date.valueOf(periodStartDate),
            Date.valueOf(targetDate),
            jobInstanceId,
            period.name());

    jdbcTemplate.update(
        """
        DELETE FROM stg_product_rank_aggregation
        WHERE job_instance_id = ? AND period_type = ?
        """,
        jobInstanceId,
        period.name());

    log.info(
        "랭킹 스냅샷 게시 완료: period={}, targetDate={}, insertedRows={}",
        period,
        targetDate,
        insertedRows);
    return RepeatStatus.FINISHED;
  }
}
