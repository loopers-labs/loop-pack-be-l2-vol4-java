package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.RankItemProcessor;
import com.loopers.batch.job.ranking.step.RankMvItemWriter;
import com.loopers.batch.listener.JobListener;
import com.loopers.ranking.domain.ProductRankModel;
import com.loopers.ranking.domain.RankingWeightProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 주간/월간 랭킹 집계 Job — 단일 aggregateStep으로 product_metrics를 점수순 TOP 100으로 읽어
 * rank를 매기고 MV에 적재한다.
 *
 * Reader가 SQL(LOG10 포함)로 점수를 계산해 정렬·LIMIT 100 하므로, 청크를 넘나드는 전역 순위 없이도
 * Processor가 흘러오는 순서대로 rank 1..N을 부여할 수 있다.
 * 비우기(clear)는 별도 Step이 아니라 Writer가 같은 청크 트랜잭션에서 수행한다 — 실패 시 이전 판이
 * 그대로 살아남고(fail = keep old), 조회가 빈 MV를 보는 창도 없앤다. (RankMvItemWriter 참고)
 * 주의: 정렬용 SQL 점수식은 RankingScorePolicy와 동일해야 순위가 일치한다(의도적 결합).
 * → 어긋남은 RankItemProcessor가 점수 단조성 검증으로 런타임에 잡는다.
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankAggregationJobConfig {

    public static final String JOB_NAME = "rankAggregationJob";
    private static final int TOP_N = 100;
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final RankingWeightProperties weights;
    private final JobListener jobListener;
    private final RankItemProcessor rankItemProcessor;
    private final RankMvItemWriter rankMvItemWriter;

    @Bean(JOB_NAME)
    public Job rankAggregationJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(aggregateStep())
            .listener(jobListener)
            .build();
    }

    @Bean("rankAggregateStep")
    public Step aggregateStep() {
        return new StepBuilder("rankAggregateStep", jobRepository)
            .<ProductMetricRow, ProductRankModel>chunk(CHUNK_SIZE, transactionManager)
            .reader(metricsReader())
            .processor(rankItemProcessor)
            .writer(rankMvItemWriter)
            .build();
    }

    @Bean
    public JdbcCursorItemReader<ProductMetricRow> metricsReader() {
        // 점수식 = view*wv + like*wl + log10(1+sales)*wo  (RankingScorePolicy와 동일)
        String scoreExpr = "view_count * ? + like_count * ? + LOG10(1 + sales_count) * ?";
        String sql = "SELECT product_id, view_count, like_count, sales_count "
            + "FROM product_metrics "
            + "ORDER BY (" + scoreExpr + ") DESC "
            + "LIMIT " + TOP_N;
        return new JdbcCursorItemReaderBuilder<ProductMetricRow>()
            .name("metricsReader")
            .dataSource(dataSource)
            .sql(sql)
            .preparedStatementSetter(ps -> {
                ps.setDouble(1, weights.view());
                ps.setDouble(2, weights.like());
                ps.setDouble(3, weights.order());
            })
            .rowMapper((rs, rowNum) -> new ProductMetricRow(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getLong("sales_count")))
            .build();
    }
}
