package com.loopers.tddstudy.infrastructure.batch;

import com.loopers.tddstudy.infrastructure.metrics.ProductMetrics;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDaily;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Configuration
public class SnapshotBatchConfig {

    private static final DateTimeFormatter BASE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int CHUNK_SIZE = 1000;

    @Bean
    public JpaPagingItemReader<ProductMetrics> snapshotReader(EntityManagerFactory emf) {
        return new JpaPagingItemReaderBuilder<ProductMetrics>()
                .name("snapshotReader")
                .entityManagerFactory(emf)
                .queryString("select p from ProductMetrics p")
                .pageSize(1000)
                .build();
    }

    @Bean
    public JpaItemWriter<ProductMetricsDaily> snapshotWriter(EntityManagerFactory emf) {
        return new JpaItemWriterBuilder<ProductMetricsDaily>()
                .entityManagerFactory(emf)
                .usePersist(true)
                .build();
    }



    // ① Processor를 Bean으로 (Job 파라미터 baseDate를 받아야 해서 @StepScope)
    @Bean
    @StepScope
    public DailyDeltaProcessor snapshotProcessor(
            ProductMetricsDailyJpaRepository dailyRepository,
            @Value("#{jobParameters['baseDate']}") String baseDate) {
        // 하루도 기록된 적이 없다 = 이번이 최초 실행(콜드 스타트).
        // 상품 단위가 아니라 '시스템 단위'로 판단해야 한다 — 운영 도중 등장한 신규 상품도
        // 자기 daily 행은 없지만, 그 활동은 오늘 일어난 것이 맞기 때문.
        boolean baselineRun = dailyRepository.count() == 0;
        return new DailyDeltaProcessor(
                dailyRepository, LocalDate.parse(baseDate, BASE_DATE_FORMAT), baselineRun);
    }

    // ② Step 1 — 오늘 날짜 행 삭제 (Tasklet)
    @Bean
    public Step clearDailyStep(JobRepository jobRepository,
                               PlatformTransactionManager txManager,
                               ProductMetricsDailyJpaRepository dailyRepository) {
        return new StepBuilder("clearDailyStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String baseDate = (String) chunkContext.getStepContext()
                            .getJobParameters().get("baseDate");
                    dailyRepository.deleteByMetricDate(LocalDate.parse(baseDate, BASE_DATE_FORMAT));
                    return RepeatStatus.FINISHED;
                }, txManager)
                .build();
    }

    // ③ Step 2 — 읽기·가공·저장 (Chunk)
    @Bean
    public Step snapshotStep(JobRepository jobRepository,
                             PlatformTransactionManager txManager,
                             JpaPagingItemReader<ProductMetrics> snapshotReader,
                             DailyDeltaProcessor snapshotProcessor,
                             JpaItemWriter<ProductMetricsDaily> snapshotWriter) {
        return new StepBuilder("snapshotStep", jobRepository)
                .<ProductMetrics, ProductMetricsDaily>chunk(CHUNK_SIZE, txManager)
                .reader(snapshotReader)
                .processor(snapshotProcessor)
                .writer(snapshotWriter)
                .build();
    }

    // ④ Job — Step들을 순서대로 엮음
    @Bean
    public Job dailySnapshotJob(JobRepository jobRepository,
                                Step clearDailyStep,
                                Step snapshotStep) {
        return new JobBuilder("dailySnapshotJob", jobRepository)
                .start(clearDailyStep)
                .next(snapshotStep)
                .build();
    }

}
