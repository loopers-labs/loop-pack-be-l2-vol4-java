package com.loopers.tddstudy.infrastructure.batch;

import com.loopers.tddstudy.infrastructure.metrics.ProductMetrics;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDaily;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import com.loopers.tddstudy.infrastructure.metrics.ProductMetricsJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(SnapshotBatchIntegrationTest.TestConfig.class)
class SnapshotBatchIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        JobLauncherTestUtils jobLauncherTestUtils(Job dailySnapshotJob,
                                                  JobLauncher jobLauncher,
                                                  JobRepository jobRepository) {
            JobLauncherTestUtils utils = new JobLauncherTestUtils();
            utils.setJob(dailySnapshotJob);
            utils.setJobLauncher(jobLauncher);
            utils.setJobRepository(jobRepository);
            return utils;
        }
    }

    @Autowired JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired ProductMetricsJpaRepository metricsRepository;
    @Autowired ProductMetricsDailyJpaRepository dailyRepository;

    @BeforeEach
    void setUp() {
        dailyRepository.deleteAll();
        metricsRepository.deleteAll();
    }

    private JobParameters params(String baseDate) {
        return new JobParametersBuilder()
                .addString("baseDate", baseDate)
                .addLong("run.id", System.nanoTime())   // 매번 다르게 → 재실행 허용
                .toJobParameters();
    }

    @Test
    void 최초_스냅샷의_누적분은_오늘이_아니라_기준일에_쌓인다() throws Exception {
        // given: 배치 도입 전부터 쌓여온 누적 like=10, sales=5
        ProductMetrics m = new ProductMetrics(1L);
        m.addLike(10);
        m.addSales(5);
        metricsRepository.save(m);

        // when
        JobExecution execution = jobLauncherTestUtils.launchJob(params("20260721"));

        // then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        List<ProductMetricsDaily> rows = dailyRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getProductId()).isEqualTo(1L);
        assertThat(rows.get(0).getLikeCount()).isEqualTo(10);      // 값은 보존 (불변식)
        assertThat(rows.get(0).getSalesCount()).isEqualTo(5);
        // 오늘(7/21)이 아니라 집계 창 밖에 기록되어야 한다
        assertThat(rows.get(0).getMetricDate()).isEqualTo(DailyDeltaProcessor.BASELINE_DATE);
    }

    @Test
    void 최초_스냅샷의_누적분은_주간_집계에_잡히지_않는다() throws Exception {
        // 6개월간 쌓인 스테디셀러
        ProductMetrics old = new ProductMetrics(1L);
        old.addLike(50_000);
        metricsRepository.save(old);

        jobLauncherTestUtils.launchJob(params("20260721"));   // 최초 실행

        // 7/20~7/26 주간 창에 이 누적분이 들어오면 안 된다
        List<ProductMetricsDaily> inThatWeek = dailyRepository.findAll().stream()
                .filter(r -> !r.getMetricDate().isBefore(LocalDate.of(2026, 7, 20))
                          && !r.getMetricDate().isAfter(LocalDate.of(2026, 7, 26)))
                .toList();

        assertThat(inThatWeek).isEmpty();
    }

    @Test
    void 운영_도중_등장한_신규_상품의_활동은_그날_날짜로_기록된다() throws Exception {
        // given: 최초 실행으로 기준선이 잡힌 상태
        ProductMetrics existing = new ProductMetrics(1L);
        existing.addLike(10);
        metricsRepository.save(existing);
        jobLauncherTestUtils.launchJob(params("20260721"));

        // 다음 날, 신규 상품이 등장해 좋아요 100을 받음
        ProductMetrics brandNew = new ProductMetrics(2L);
        brandNew.addLike(100);
        metricsRepository.save(brandNew);

        // when
        jobLauncherTestUtils.launchJob(params("20260722"));

        // then: 신규 상품도 daily 이력이 없지만, 기준일이 아닌 '오늘'로 기록되어야 한다
        ProductMetricsDaily row = dailyRepository.findAll().stream()
                .filter(r -> r.getProductId().equals(2L))
                .findFirst().orElseThrow();

        assertThat(row.getMetricDate()).isEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(row.getLikeCount()).isEqualTo(100);
    }

    @Test
    void 같은_날_두_번_실행해도_값이_중복되지_않는다() throws Exception {
        ProductMetrics m = new ProductMetrics(1L);
        m.addLike(10);
        metricsRepository.save(m);

        jobLauncherTestUtils.launchJob(params("20260721"));
        jobLauncherTestUtils.launchJob(params("20260721"));   // 재실행!

        List<ProductMetricsDaily> rows = dailyRepository.findAll();
        assertThat(rows).hasSize(1);              // 행이 2개로 늘지 않음
        assertThat(rows.get(0).getLikeCount()).isEqualTo(10);   // 20으로 뻥튀기 안 됨
    }

    @Test
    void 다음날에는_늘어난_만큼만_델타로_저장된다() throws Exception {
        // given: 7/21에 누적 10
        ProductMetrics m = new ProductMetrics(1L);
        m.addLike(10);
        metricsRepository.save(m);
        jobLauncherTestUtils.launchJob(params("20260721"));

        // 하루 동안 15 더 늘어서 누적 25가 됨
        ProductMetrics updated = metricsRepository.findById(1L).orElseThrow();
        updated.addLike(15);
        metricsRepository.save(updated);

        // when: 7/22 스냅샷
        jobLauncherTestUtils.launchJob(params("20260722"));

        // then: 7/22 행은 25가 아니라 '늘어난 15'
        List<ProductMetricsDaily> rows = dailyRepository.findAll();
        assertThat(rows).hasSize(2);

        ProductMetricsDaily day22 = rows.stream()
                .filter(r -> r.getMetricDate().equals(LocalDate.of(2026, 7, 22)))
                .findFirst().orElseThrow();
        assertThat(day22.getLikeCount()).isEqualTo(15);
    }

    @Test
    void 변화가_없는_상품은_행을_만들지_않는다() throws Exception {
        ProductMetrics m = new ProductMetrics(1L);
        m.addLike(10);
        metricsRepository.save(m);
        jobLauncherTestUtils.launchJob(params("20260721"));

        // 아무 변화 없이 다음날 스냅샷
        jobLauncherTestUtils.launchJob(params("20260722"));

        // 7/22 행은 안 생김 (Processor가 null 리턴해서 걸러짐)
        assertThat(dailyRepository.findAll()).hasSize(1);
    }
}
