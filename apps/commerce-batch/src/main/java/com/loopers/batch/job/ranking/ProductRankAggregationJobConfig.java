package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.dto.ProductMetricPeriodSum;
import com.loopers.batch.job.ranking.dto.ProductRankRow;
import com.loopers.batch.job.ranking.step.AssignRankTasklet;
import com.loopers.batch.job.ranking.step.ClearMvTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.DateRange;
import com.loopers.domain.ranking.RankPeriod;
import com.loopers.domain.ranking.RankScoreWeights;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

// product_metrics_daily(일별 집계)를 읽어 주간/월간 랭킹 MV 를 만드는 배치.
// 파라미터: baseDate(LocalDate, 식별) + period(WEEKLY|MONTHLY, 식별).
// 기간 파라미터로 JobInstance 를 식별하므로 RunIdIncrementer 를 붙이지 않는다(같은 기간 중복 성공 방지).
//
// 멱등한 재실행을 위해 delete → recalculate → insert 순서의 3-Step 으로 구성한다.
//   1) clearMvStep     : 대상 기간(week_start_date/month_start_date) MV 행 삭제  -> ClearMvTasklet
//   2) aggregateStep   : (Chunk) 일별 집계를 기간·상품별 합산 → 점수 계산 → MV 적재  -> Reader/Processor/Writer
//   3) assignRankStep  : score DESC 로 순위 부여 후 TOP 100 초과분 삭제            -> AssignRankTasklet
//
// 이 클래스는 Job/Step 배선과 Chunk I/O(Reader/Processor/Writer) 정의를 담당하고,
// 단일 작업 Tasklet(삭제/순위 부여)은 step 패키지로 분리한다.
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class ProductRankAggregationJobConfig {

    public static final String JOB_NAME = "productRankAggregationJob";
    private static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    @Bean(JOB_NAME)
    public Job productRankAggregationJob(Step clearMvStep, Step aggregateStep, Step assignRankStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .validator(new DefaultJobParametersValidator(
                new String[]{"baseDate", "period"},
                new String[]{}
            ))
            .start(clearMvStep)
            .next(aggregateStep)
            .next(assignRankStep)
            .listener(jobListener)
            .build();
    }

    // ---------------------------------------------------------------- Steps

    @Bean
    public Step clearMvStep(ClearMvTasklet clearMvTasklet) {
        return new StepBuilder("clearMvStep", jobRepository)
            .tasklet(clearMvTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean
    public Step aggregateStep(
        JdbcPagingItemReader<ProductMetricPeriodSum> dailyMetricReader,
        ItemProcessor<ProductMetricPeriodSum, ProductRankRow> productRankProcessor,
        JdbcBatchItemWriter<ProductRankRow> productRankWriter
    ) {
        return new StepBuilder("aggregateStep", jobRepository)
            .<ProductMetricPeriodSum, ProductRankRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(dailyMetricReader)
            .processor(productRankProcessor)
            .writer(productRankWriter)
            .listener(stepMonitorListener)
            .build();
    }

    @Bean
    public Step assignRankStep(AssignRankTasklet assignRankTasklet) {
        return new StepBuilder("assignRankStep", jobRepository)
            .tasklet(assignRankTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    // ---------------------------------------------------------------- Chunk I/O (프레임워크 Reader/Writer)

    // product_metrics_daily 를 기간으로 필터링해 상품별로 view/like/order 를 합산한다.
    // GROUP BY 페이징이므로 정렬/경계 키는 그룹 키(product_id)로 둔다(점수는 이후 순위 단계에서 정렬).
    @Bean
    @StepScope
    public JdbcPagingItemReader<ProductMetricPeriodSum> dailyMetricReader(
        DataSource dataSource,
        @Value("#{jobParameters['baseDate']}") LocalDate baseDate,
        @Value("#{jobParameters['period']}") String period
    ) throws Exception {
        DateRange range = RankPeriod.from(period).resolveRange(baseDate);

        SqlPagingQueryProviderFactoryBean providerFactory = new SqlPagingQueryProviderFactoryBean();
        providerFactory.setDataSource(dataSource);
        providerFactory.setSelectClause(
            "product_id, SUM(view_count) AS view_count, SUM(like_count) AS like_count, SUM(order_count) AS order_count"
        );
        providerFactory.setFromClause("FROM product_metrics_daily");
        providerFactory.setWhereClause("metric_date BETWEEN :startDate AND :endDate");
        providerFactory.setGroupClause("product_id");
        providerFactory.setSortKeys(Map.of("product_id", Order.ASCENDING));
        PagingQueryProvider queryProvider = providerFactory.getObject();

        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("startDate", range.start());
        parameterValues.put("endDate", range.end());

        return new JdbcPagingItemReaderBuilder<ProductMetricPeriodSum>()
            .name("dailyMetricReader")
            .dataSource(dataSource)
            .queryProvider(queryProvider)
            .parameterValues(parameterValues)
            .pageSize(CHUNK_SIZE)
            .rowMapper((rs, rowNum) -> new ProductMetricPeriodSum(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getLong("order_count")
            ))
            .build();
    }

    // 상품별 합산 건수에 가중치를 적용해 점수를 계산한다. 점수 0 이하 상품은 랭킹에서 제외한다(null 반환 → Writer 미전달).
    @Bean
    @StepScope
    public ItemProcessor<ProductMetricPeriodSum, ProductRankRow> productRankProcessor(
        @Value("#{jobParameters['baseDate']}") LocalDate baseDate,
        @Value("#{jobParameters['period']}") String period
    ) {
        LocalDate aggregateDate = RankPeriod.from(period).aggregateDate(baseDate);
        return item -> {
            long score = RankScoreWeights.DEFAULT.score(item.viewCount(), item.likeCount(), item.orderCount());
            if (score <= 0) {
                return null;
            }
            return new ProductRankRow(aggregateDate, item.productId(), score);
        };
    }

    // MV 에 insert 한다. 대상 테이블명은 period(enum)로만 결정되므로 SQL 문자열 조립에 사용해도 안전하다.
    // ranking 은 placeholder(0)로 넣고 assignRankStep 에서 채운다.
    @Bean
    @StepScope
    public JdbcBatchItemWriter<ProductRankRow> productRankWriter(
        DataSource dataSource,
        @Value("#{jobParameters['period']}") String period
    ) {
        RankPeriod rankPeriod = RankPeriod.from(period);
        String table = rankPeriod.tableName();
        String dateColumn = rankPeriod.dateColumnName();
        String sql = "INSERT INTO " + table
            + " (" + dateColumn + ", product_id, score, ranking, created_at, updated_at)"
            + " VALUES (:aggregateDate, :productId, :score, 0, NOW(), NOW())";
        return new JdbcBatchItemWriterBuilder<ProductRankRow>()
            .dataSource(dataSource)
            .sql(sql)
            .itemSqlParameterSourceProvider(row -> new MapSqlParameterSource()
                .addValue("aggregateDate", row.aggregateDate())
                .addValue("productId", row.productId())
                .addValue("score", row.score()))
            .build();
    }
}
