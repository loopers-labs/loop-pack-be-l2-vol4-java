package com.loopers.batch.job.productranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@RequiredArgsConstructor
public class StagingInitializationTasklet implements Tasklet {

  private final JdbcTemplate jdbcTemplate;
  private final long jobInstanceId;

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    int deletedRows =
        jdbcTemplate.update(
            "DELETE FROM stg_product_rank_aggregation WHERE job_instance_id = ?", jobInstanceId);
    log.info("랭킹 staging 초기화 완료: jobInstanceId={}, deletedRows={}", jobInstanceId, deletedRows);
    return RepeatStatus.FINISHED;
  }
}
