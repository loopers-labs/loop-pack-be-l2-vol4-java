package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.chunk.AggregatedScore;
import com.loopers.batch.job.ranking.chunk.RankedRow;
import com.loopers.batch.listener.JobListener;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
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
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 주간 랭킹의 Chunk-Oriented 버전(Tasklet 버전과 병행). 랭킹은 전역 정렬+TOP100 이라 본래 Chunk 와 결이 다르지만,
 * "대량 데이터를 Reader→Processor→Writer 로 흘려보내는" 구조를 학습하기 위한 구현이다.
 * <p>
 * 2-Step: ① DELETE(Tasklet)로 그 주를 비우고 ② Chunk 로 집계 결과를 rank 부여해 적재한다.
 * Tasklet 버전의 '한 트랜잭션 DELETE→INSERT'와 달리 두 Step 은 별개 트랜잭션이라 완전 원자성은 양보한다(트레이드오프).
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingChunkJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingChunkJobConfig {

    public static final String JOB_NAME = "weeklyRankingChunkJob";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JobListener jobListener;
    private final MvProductRankWeeklyJpaRepository mvRepository;

    @Bean(JOB_NAME)
    public Job weeklyRankingChunkJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(weeklyChunkDeleteStep())
            .next(weeklyChunkAggregateStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean("weeklyChunkDeleteStep")
    public Step weeklyChunkDeleteStep() {
        return new StepBuilder("weeklyChunkDeleteStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                LocalDate requestDate = (LocalDate) chunkContext.getStepContext().getJobParameters().get("requestDate");
                mvRepository.deleteByYearWeek(RankingWeek.from(requestDate).yearWeek());
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    @JobScope
    @Bean("weeklyChunkAggregateStep")
    public Step weeklyChunkAggregateStep() {
        return new StepBuilder("weeklyChunkAggregateStep", jobRepository)
            .<AggregatedScore, RankedRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(weeklyChunkReader(null))
            .processor(weeklyChunkProcessor(null))
            .writer(weeklyChunkWriter())
            .build();
    }

    /**
     * 기간의 daily 를 상품별로 롤업해 score DESC 로 커서 스트리밍한다(TOP 100). 페이징 대신 커서라 집계+정렬을 그대로 흘려보낸다.
     */
    @StepScope
    @Bean
    public JdbcCursorItemReader<AggregatedScore> weeklyChunkReader(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        RankingWeek week = RankingWeek.from(requestDate);
        return new JdbcCursorItemReaderBuilder<AggregatedScore>()
            .name("weeklyChunkReader")
            .dataSource(dataSource)
            .sql("""
                SELECT product_id,
                       SUM(view_count) * 0.1 + SUM(like_count) * 0.2 + SUM(sales_count) * 0.7 AS score
                FROM daily_product_metrics
                WHERE metric_date BETWEEN ? AND ?
                GROUP BY product_id
                ORDER BY score DESC
                LIMIT 100
                """)
            .preparedStatementSetter(ps -> {
                ps.setDate(1, Date.valueOf(week.startDate()));
                ps.setDate(2, Date.valueOf(week.endDate()));
            })
            .rowMapper((rs, rowNum) -> new AggregatedScore(rs.getLong("product_id"), rs.getDouble("score")))
            .build();
    }

    /**
     * Reader 가 score DESC 로 주는 순서대로 1-based rank 를 부여한다. StepScope 라 Step 당 새 카운터(청크 경계 넘어 연속).
     */
    @StepScope
    @Bean
    public ItemProcessor<AggregatedScore, RankedRow> weeklyChunkProcessor(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        String yearWeek = RankingWeek.from(requestDate).yearWeek();
        AtomicInteger rank = new AtomicInteger(0);
        return item -> new RankedRow(yearWeek, item.productId(), item.score(), rank.incrementAndGet());
    }

    @Bean
    public JdbcBatchItemWriter<RankedRow> weeklyChunkWriter() {
        return new JdbcBatchItemWriterBuilder<RankedRow>()
            .dataSource(dataSource)
            .sql("""
                INSERT INTO mv_product_rank_weekly (year_week, product_id, score, rank_no, updated_at)
                VALUES (:yearWeek, :productId, :score, :rankNo, NOW())
                """)
            .itemSqlParameterSourceProvider(row -> new MapSqlParameterSource()
                .addValue("yearWeek", row.yearWeek())
                .addValue("productId", row.productId())
                .addValue("score", row.score())
                .addValue("rankNo", row.rankNo()))
            .build();
    }
}
