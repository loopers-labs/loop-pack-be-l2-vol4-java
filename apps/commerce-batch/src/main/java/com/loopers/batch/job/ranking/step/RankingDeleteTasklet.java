package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.domain.RankingPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

/**
 * 대상 기간을 비운다. 청크 루프 안이 아니라 별도 Step 이라 재시작 시 스킵되고,
 * 그래야 Step2 가 이어서 채운 행을 다시 지우지 않는다.
 * 지울 것이 없어도 성공으로 끝난다 — 첫 실행과 재실행의 결과가 같아야 하기 때문이다.
 */
@Slf4j
@RequiredArgsConstructor
public class RankingDeleteTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final RankingPeriod period;
    private final LocalDate targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        RankingPeriod.Range range = period.resolve(targetDate);

        int deleted = jdbcTemplate.update(
                "DELETE FROM %s WHERE period_key = ?".formatted(period.tableName()), range.periodKey());

        log.info("ranking mv cleared period={} key={} deleted={}", period, range.periodKey(), deleted);
        return RepeatStatus.FINISHED;
    }
}
