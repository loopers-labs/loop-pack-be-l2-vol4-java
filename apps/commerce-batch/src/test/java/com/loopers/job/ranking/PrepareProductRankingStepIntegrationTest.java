package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.ProductRankingSnapshotJobConfig;
import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.ProductRankingSnapshotRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.batch.job.name=" + ProductRankingSnapshotJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
@SpringBatchTest
@Import(PrepareProductRankingStepIntegrationTest.TestJobConfiguration.class)
class PrepareProductRankingStepIntegrationTest {

    private static final String TEST_JOB_NAME = "prepareProductRankingStepTestJob";
    private static final Instant CREATED_AT = Instant.parse("2026-07-20T02:00:00.123456Z");
    private static final ProductRankingSnapshotKey SNAPSHOT_KEY = new ProductRankingSnapshotKey(
        RankingPeriod.WEEKLY,
        LocalDate.of(2026, 7, 19),
        1
    );

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier(TEST_JOB_NAME)
    private Job testJob;

    @Autowired
    private ProductRankingSnapshotRepository snapshotRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(testJob);
        when(clock.instant()).thenReturn(CREATED_AT);
    }

    @AfterEach
    void tearDown() {
        jobRepositoryTestUtils.removeJobExecutions();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("prepareProductRankingStep은 실행 키와 현재 정책으로 미완성 Snapshot을 커밋한다.")
    @Test
    void commitsIncompleteSnapshot() throws Exception {
        // arrange
        JobParameters jobParameters = new JobParametersBuilder()
            .addString("period", "WEEKLY")
            .addString("aggregationEndDate", "20260719")
            .addLong("revision", 1L)
            .toJobParameters();

        // act
        JobExecution execution = jobLauncherTestUtils.launchJob(jobParameters);

        // assert
        ProductRankingSnapshotHeader snapshot = snapshotRepository.findBy(SNAPSHOT_KEY)
            .orElseThrow();
        assertAll(
            () -> assertThat(execution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED),
            () -> assertThat(execution.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly(ProductRankingSnapshotJobConfig.PREPARE_STEP_NAME),
            () -> assertThat(snapshot.key()).isEqualTo(SNAPSHOT_KEY),
            () -> assertThat(snapshot.scorePolicy().version()).isEqualTo("V1"),
            () -> assertThat(snapshot.createdAt()).isEqualTo(CREATED_AT),
            () -> assertThat(snapshot.completedAt()).isNull()
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJobConfiguration {

        @Bean(TEST_JOB_NAME)
        Job prepareProductRankingStepTestJob(
            JobRepository jobRepository,
            @Qualifier(ProductRankingSnapshotJobConfig.PREPARE_STEP_NAME) Step prepareStep
        ) {
            return new JobBuilder(TEST_JOB_NAME, jobRepository)
                .start(prepareStep)
                .build();
        }
    }
}
