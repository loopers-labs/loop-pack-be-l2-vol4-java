package com.loopers.ranking;

import com.loopers.batch.job.ranking.RankAggregationJobConfig;
import com.loopers.batch.job.ranking.step.RankItemProcessor;
import com.loopers.metrics.domain.ProductMetricsModel;
import com.loopers.metrics.infrastructure.ProductMetricsJpaRepository;
import com.loopers.ranking.domain.WeeklyProductRankModel;
import com.loopers.ranking.infrastructure.WeeklyProductRankJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 집계 도중 실패했을 때 "이전 판이 그대로 살아남는다(fail = keep old)"를 검증한다.
 * Processor가 예외를 던지면 청크 트랜잭션이 롤백돼 Writer의 비우기+채우기가 아예 반영되지 않으므로,
 * 실행 전 MV가 손상 없이 유지돼야 한다.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + RankAggregationJobConfig.JOB_NAME)
class RankAggregationJobRollbackTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(RankAggregationJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private WeeklyProductRankJpaRepository weeklyRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private RankItemProcessor rankItemProcessor;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private JobParameters params(String period) {
        return new JobParametersBuilder()
            .addString("period", period)
            .addLong("run", System.nanoTime())
            .toJobParameters();
    }

    @DisplayName("집계 중 예외가 발생하면 Job은 실패하고, 기존 MV는 롤백되어 그대로 유지된다.")
    @Test
    void keepsExistingMv_whenAggregationFails() throws Exception {
        // arrange — 기존 MV에 마지막 정상 판 1행, 새로 읽을 원본 1행
        weeklyRepository.save(new WeeklyProductRankModel(999L, 1, 123.0));
        productMetricsJpaRepository.save(new ProductMetricsModel(1L, 0, 0, 100));
        given(rankItemProcessor.process(any())).willThrow(new RuntimeException("집계 중 강제 실패"));
        jobLauncherTestUtils.setJob(job);

        // act
        var execution = jobLauncherTestUtils.launchJob(params("weekly"));

        // assert — Job 실패 + 기존 판이 손상 없이 유지(비우기 미반영)
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
        List<WeeklyProductRankModel> remaining = weeklyRepository.findAllByOrderByRankAsc();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getProductId()).isEqualTo(999L);
    }
}
