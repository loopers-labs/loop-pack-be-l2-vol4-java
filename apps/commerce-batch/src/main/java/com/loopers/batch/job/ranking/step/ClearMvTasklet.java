package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.ProductRankAggregationJobConfig;
import com.loopers.domain.ranking.RankPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDate;

// delete → recalculate → insert 전략의 첫 단계. 대상 기간 MV 행을 먼저 비워 재실행 시 중복 적재를 막는다.
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class ClearMvTasklet implements Tasklet {

    private final DataSource dataSource;

    @Value("#{jobParameters['baseDate']}")
    private LocalDate baseDate;

    @Value("#{jobParameters['period']}")
    private String period;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        RankPeriod rankPeriod = RankPeriod.from(period);
        String table = rankPeriod.tableName();
        String dateColumn = rankPeriod.dateColumnName();
        LocalDate aggregateDate = rankPeriod.aggregateDate(baseDate);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        int deleted = jdbcTemplate.update("DELETE FROM " + table + " WHERE " + dateColumn + " = ?", aggregateDate);
        log.info("MV 초기화: table={}, aggregateDate={}, deleted={}", table, aggregateDate, deleted);
        return RepeatStatus.FINISHED;
    }
}
