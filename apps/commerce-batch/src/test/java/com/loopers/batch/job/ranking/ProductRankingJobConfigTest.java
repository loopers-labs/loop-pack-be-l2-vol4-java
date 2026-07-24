package com.loopers.batch.job.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.batch.job.enabled=false",
    "spring.batch.job.name=" + ProductRankingJobConfig.JOB_NAME
})
@SpringBatchTest
class ProductRankingJobConfigTest {

    @MockitoBean
    private RedissonClient redissonClient;

    @MockitoBean(name = "defaultRedisConnectionFactory")
    private LettuceConnectionFactory defaultRedisConnectionFactory;

    @MockitoBean(name = "masterRedisConnectionFactory")
    private LettuceConnectionFactory masterRedisConnectionFactory;

    @Autowired
    @Qualifier(ProductRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Test
    @DisplayName("productRankingAggregationJob Bean을 로딩한다.")
    void contextLoads_ProductRankingAggregationJob() {
        assertThat(job.getName()).isEqualTo(ProductRankingJobConfig.JOB_NAME);
    }

    @Test
    @DisplayName("WEEKLY 파라미터가 월요일부터 일요일까지 7일 범위가 아니면 Job 실행 전에 실패한다.")
    void launchJob_WhenInvalidWeeklyRange_ShouldFailBeforeStep() {
        jobLauncherTestUtils.setJob(job);
        var jobParameters = new JobParametersBuilder()
            .addString("period", "WEEKLY")
            .addString("startDate", "20260721")
            .addString("endDate", "20260727")
            .toJobParameters();

        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters))
            .isInstanceOf(JobParametersInvalidException.class);
    }
}
