package com.loopers.tddstudy.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@ConditionalOnProperty(name = "batch.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class BatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduler.class);
    private static final DateTimeFormatter BASE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ZONE = "Asia/Seoul";

    private final JobLauncher jobLauncher;
    private final Job dailySnapshotJob;
    private final Job rankAggregationJob;

    public BatchScheduler(JobLauncher jobLauncher,
                          Job dailySnapshotJob,
                          Job rankAggregationJob) {
        this.jobLauncher = jobLauncher;
        this.dailySnapshotJob = dailySnapshotJob;
        this.rankAggregationJob = rankAggregationJob;
    }

    /** 매일 23:50 — 오늘 하루치 델타를 product_metrics_daily 에 적재 */
    @Scheduled(cron = "0 50 23 * * *", zone = ZONE)
    public void runDailySnapshot() {
        launch(dailySnapshotJob, "dailySnapshotJob");
    }

    /** 매일 23:55 — 직전에 끝난 주/달을 MV 에 집계 */
    @Scheduled(cron = "0 55 23 * * *", zone = ZONE)
    public void runRankAggregation() {
        launch(rankAggregationJob, "rankAggregationJob");
    }

    private void launch(Job job, String jobName) {
        LocalDate baseDate = LocalDate.now(ZoneId.of(ZONE));
        try {
            jobLauncher.run(job, paramsOf(baseDate));
        } catch (Exception e) {
            // 스케줄러 스레드가 죽지 않도록 삼키고 기록만 남긴다 (다음 날 재실행으로 자가 복구)
            log.error("{} 실행 실패 (baseDate={})", jobName, baseDate, e);
        }
    }

    private JobParameters paramsOf(LocalDate baseDate) {
        return new JobParametersBuilder()
                .addString("baseDate", baseDate.format(BASE_DATE_FORMAT))
                .toJobParameters();
    }
}
