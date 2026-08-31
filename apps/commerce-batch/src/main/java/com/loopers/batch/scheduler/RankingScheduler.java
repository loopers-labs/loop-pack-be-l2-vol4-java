package com.loopers.batch.scheduler;

import com.loopers.batch.job.ranking.monthly.MonthlyRankingJobConfig;
import com.loopers.batch.job.ranking.weekly.WeeklyRankingJobConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingScheduler {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobLauncher jobLauncher;

    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private final Job weeklyRankingJob;

    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private final Job monthlyRankingJob;

    // 매주 월요일 새벽 1시 — 지난 주 집계
    @Scheduled(cron = "0 0 1 * * MON", zone = "Asia/Seoul")
    public void runWeeklyRankingJob() {
        String targetDate = LocalDate.now().minusDays(1).format(DATE_FORMAT);
        run(weeklyRankingJob, targetDate);
    }

    // 매월 1일 새벽 1시 — 지난 달 집계
    @Scheduled(cron = "0 0 1 1 * *", zone = "Asia/Seoul")
    public void runMonthlyRankingJob() {
        String targetDate = LocalDate.now().minusDays(1).format(DATE_FORMAT);
        run(monthlyRankingJob, targetDate);
    }

    private void run(Job job, String targetDate) {
        try {
            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", targetDate)
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
            jobLauncher.run(job, params);
        } catch (Exception e) {
            log.error("Job 실행 실패 — job={}, targetDate={}", job.getName(), targetDate, e);
        }
    }
}
