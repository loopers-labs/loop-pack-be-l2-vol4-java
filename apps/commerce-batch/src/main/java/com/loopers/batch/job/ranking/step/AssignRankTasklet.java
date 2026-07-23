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

// 적재 완료 후, score DESC(동점은 product_id ASC)로 순위를 부여하고 TOP 100 초과분을 삭제한다.
// ROW_NUMBER() 윈도우 함수 + UPDATE ... JOIN 은 MySQL 8 벤더 기능이라 native SQL 로 처리한다.
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class AssignRankTasklet implements Tasklet {

    private static final int TOP_N = 100;

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
        jdbcTemplate.update(
            "UPDATE " + table + " t"
                + " JOIN (SELECT id, ROW_NUMBER() OVER (ORDER BY score DESC, product_id ASC) AS rnk"
                + " FROM " + table + " WHERE " + dateColumn + " = ?) r ON t.id = r.id"
                + " SET t.ranking = r.rnk, t.updated_at = NOW()"
                + " WHERE t." + dateColumn + " = ?",
            aggregateDate, aggregateDate
        );
        int trimmed = jdbcTemplate.update(
            "DELETE FROM " + table + " WHERE " + dateColumn + " = ? AND ranking > ?",
            aggregateDate, TOP_N
        );
        log.info("순위 부여 및 TOP {} 유지: table={}, aggregateDate={}, trimmed={}", TOP_N, table, aggregateDate, trimmed);
        return RepeatStatus.FINISHED;
    }
}
