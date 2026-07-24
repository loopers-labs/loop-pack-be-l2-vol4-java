package com.loopers.batch.job.productrank;

import com.loopers.batch.job.productrank.step.MvPurgeTasklet;
import com.loopers.batch.job.productrank.step.MvRankTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.AggregationTarget;
import com.loopers.domain.ranking.MvProductRank;
import com.loopers.domain.ranking.ProductMetricsSum;
import com.loopers.domain.ranking.RankingProperties;
import com.loopers.domain.ranking.RankingScorePolicy;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 상품 랭킹 MV 집계 Job — 일간 집계(product_metrics)를 주간/월간으로 압착해 조회 전용 테이블에 적재한다.
 *
 * <p>실행 예: {@code --job.name=productRankAggregationJob baseDate=20260722 period=WEEKLY}
 *
 * <p>3단계로 나눈 이유
 * <ol>
 *   <li>purge — 같은 파라미터 재실행을 멱등하게 만든다(부분 실패 후 재실행 포함).</li>
 *   <li>aggregate — 청크 지향. 기간 내 전 상품을 페이지 단위로 읽어 점수를 매겨 적재한다.</li>
 *   <li>rank — 전체를 본 뒤에야 가능한 "정렬 후 상위 N"을 집합 연산으로 확정한다.</li>
 * </ol>
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class ProductRankAggregationJobConfig {

    public static final String JOB_NAME = "productRankAggregationJob";
    private static final String STEP_PURGE = "mvPurgeStep";
    private static final String STEP_AGGREGATE = "mvAggregateStep";
    private static final String STEP_RANK = "mvRankStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;
    private final RankingScorePolicy rankingScorePolicy;
    private final RankingProperties rankingProperties;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final MvPurgeTasklet mvPurgeTasklet;
    private final MvRankTasklet mvRankTasklet;

    @Bean(JOB_NAME)
    public Job productRankAggregationJob(
        @Qualifier(STEP_PURGE) Step purgeStep,
        @Qualifier(STEP_AGGREGATE) Step aggregateStep,
        @Qualifier(STEP_RANK) Step rankStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer()) // 같은 baseDate/period 로 재실행 가능하게
            .start(purgeStep)
            .next(aggregateStep)
            .next(rankStep)
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_PURGE)
    public Step mvPurgeStep() {
        return new StepBuilder(STEP_PURGE, jobRepository)
            .tasklet(mvPurgeTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_AGGREGATE)
    public Step mvAggregateStep(
        JdbcPagingItemReader<ProductMetricsSum> productMetricsSumReader,
        ItemProcessor<ProductMetricsSum, MvProductRank> productRankProcessor,
        JpaItemWriter<MvProductRank> mvProductRankWriter
    ) {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
            .<ProductMetricsSum, MvProductRank>chunk(rankingProperties.chunkSize(), transactionManager)
            .reader(productMetricsSumReader)
            .processor(productRankProcessor)
            .writer(mvProductRankWriter)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_RANK)
    public Step mvRankStep() {
        return new StepBuilder(STEP_RANK, jobRepository)
            .tasklet(mvRankTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    /**
     * 기간 내 product_metrics 를 상품 단위로 압착해 페이지 단위로 읽는다.
     * 정렬 키(product_id)가 GROUP BY 키와 같아야 페이지 경계가 상품을 쪼개지 않는다.
     */
    @StepScope
    @Bean
    public JdbcPagingItemReader<ProductMetricsSum> productMetricsSumReader(
        @Value("#{jobParameters['baseDate']}") String baseDate,
        @Value("#{jobParameters['period']}") String period
    ) {
        AggregationTarget target = AggregationTarget.of(baseDate, period);

        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("""
            product_id,
            SUM(like_delta)  AS like_delta,
            SUM(sales_count) AS sales_count,
            SUM(view_count)  AS view_count""");
        queryProvider.setFromClause("FROM product_metrics");
        queryProvider.setWhereClause("metric_date BETWEEN :fromDate AND :toDate");
        queryProvider.setGroupClause("product_id");
        queryProvider.setSortKeys(Map.of("product_id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<ProductMetricsSum>()
            .name("productMetricsSumReader")
            .dataSource(dataSource)
            .queryProvider(queryProvider)
            .parameterValues(Map.of("fromDate", target.from(), "toDate", target.to()))
            .pageSize(rankingProperties.chunkSize())
            .rowMapper((rs, rowNum) -> new ProductMetricsSum(
                rs.getLong("product_id"),
                rs.getLong("like_delta"),
                rs.getLong("sales_count"),
                rs.getLong("view_count")))
            .build();
    }

    /** 지표합 → 가중 점수 → 기간에 맞는 MV 로우. 순위는 아직 비어 있다(rank 단계에서 확정). */
    @StepScope
    @Bean
    public ItemProcessor<ProductMetricsSum, MvProductRank> productRankProcessor(
        @Value("#{jobParameters['baseDate']}") String baseDate,
        @Value("#{jobParameters['period']}") String period
    ) {
        AggregationTarget target = AggregationTarget.of(baseDate, period);
        return sum -> MvProductRank.of(target.period(), target.periodKey(), rankingScorePolicy.score(sum));
    }

    @Bean
    public JpaItemWriter<MvProductRank> mvProductRankWriter() {
        JpaItemWriter<MvProductRank> writer = new JpaItemWriter<>();
        writer.setEntityManagerFactory(entityManagerFactory);
        return writer;
    }
}
