package com.loopers.batch.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.annotation.AfterJob;
import org.springframework.batch.core.annotation.BeforeJob;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

@Slf4j
@RequiredArgsConstructor
@Component
public class JobListener {

    @BeforeJob
    void beforeJob(JobExecution jobExecution) {
        log.info("Job '${jobExecution.jobInstance.jobName}' 시작");
        jobExecution.getExecutionContext().putLong("startTime", System.currentTimeMillis());
    }

    @AfterJob
    void afterJob(JobExecution jobExecution) {
        var startTime = jobExecution.getExecutionContext().getLong("startTime");
        var endTime = System.currentTimeMillis();

        var startDateTime = Instant.ofEpochMilli(startTime)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
        var endDateTime = Instant.ofEpochMilli(endTime)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

        var totalTime = endTime - startTime;
        var duration = Duration.ofMillis(totalTime);
        var hours = duration.toHours();
        var minutes = duration.toMinutes() % 60;
        var seconds = duration.getSeconds() % 60;

        var message = String.format(
            """
                *Start Time:* %s
                *End Time:* %s
                *Total Time:* %d시간 %d분 %d초
            """, startDateTime, endDateTime, hours, minutes, seconds
        ).trim();

        log.info(message);

        // [fix] 배치 실패가 완료 로그(info)에 묻혀 stale/빈 MV가 조용히 방치되지 않도록 실패를 ERROR로 부각한다
        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.error(
                "Job '{}' 실패 — 이전 MV가 유지됩니다(재적재 미반영). 원인: {}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getAllFailureExceptions()
            );
        }
    }
}
