package com.loopers.batch.job.productrank;

import com.loopers.batch.job.productrank.step.ProductRankMvCleanupTasklet;
import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.StepMonitorListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;

// Weekly/Monthly 롤업 Job은 집계 윈도우 길이와 대상 테이블만 다를 뿐 Step 구성이 동일하다.
// 두 JobConfig가 각자 @Bean 메서드를 선언하되(스코프 프록시 때문에 @Bean 선언 자체는 각 Configuration에
// 남겨둔다), 실제 Step/Reader 조립 로직은 여기로 모아 중복을 없앤다.
public final class ProductRankJobSupport {

    private ProductRankJobSupport() {
    }

    public static Step cleanupStep(
            String stepName,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            StepMonitorListener stepMonitorListener,
            ProductRankMvCleanupTasklet tasklet
    ) {
        return new StepBuilder(stepName, jobRepository)
                .tasklet(tasklet, transactionManager)
                .listener(stepMonitorListener)
                // 실패 후 같은 requestDate로 재시작하면 Spring Batch는 기본적으로 이미
                // COMPLETED된 스텝을 건너뛴다. cleanup을 건너뛰면 "매번 처음부터 재계산" 전제가
                // 깨지므로, 재시작 시에도 cleanup이 항상 다시 실행되도록 허용한다.
                .allowStartIfComplete(true)
                .build();
    }

    public static Step aggregateStep(
            String stepName,
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            int chunkSize,
            JdbcCursorItemReader<ProductMetricDailyRow> reader,
            ProductRankScoreProcessor processor,
            ProductRankMvUpsertWriter writer,
            StepMonitorListener stepMonitorListener,
            ChunkListener chunkListener
    ) {
        return new StepBuilder(stepName, jobRepository)
                .<ProductMetricDailyRow, ProductRankScoreDelta>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(stepMonitorListener)
                .listener(chunkListener)
                .build();
    }

    public static JdbcCursorItemReader<ProductMetricDailyRow> dailyMetricReader(
            String readerName,
            DataSource dataSource,
            LocalDate requestDate,
            int windowDays
    ) {
        LocalDate windowEnd = requestDate.minusDays(1);
        LocalDate windowStart = windowEnd.minusDays(windowDays - 1);

        return new JdbcCursorItemReaderBuilder<ProductMetricDailyRow>()
                .name(readerName)
                .dataSource(dataSource)
                // 재시작 시 이전 실행의 커서 위치(읽은 행 수)에서 이어받지 않고 항상 처음부터 다시
                // 읽도록 체크포인트 저장을 끈다. cleanup과 짝을 이뤄 "매번 완전 재계산"을 보장한다.
                .saveState(false)
                .sql("""
                        SELECT product_id, metric_date, view_count, like_delta_count, purchase_quantity
                        FROM product_metric_daily
                        WHERE metric_date BETWEEN ? AND ?
                        ORDER BY product_id
                        """)
                .preparedStatementSetter(ps -> {
                    ps.setDate(1, Date.valueOf(windowStart));
                    ps.setDate(2, Date.valueOf(windowEnd));
                })
                .rowMapper((rs, rowNum) -> new ProductMetricDailyRow(
                        rs.getString("product_id"),
                        rs.getDate("metric_date").toLocalDate(),
                        rs.getLong("view_count"),
                        rs.getLong("like_delta_count"),
                        rs.getLong("purchase_quantity")
                ))
                .build();
    }
}
