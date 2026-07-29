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
import java.util.Objects;
import java.util.stream.Collectors;

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

        notifyIfFailed(jobExecution);
    }

    /**
     * 실패하면 error 로그로 알린다 — logback Slack appender 가 붙어 있어 그대로 채널로 나간다.
     * 완료 가드 예외(이미 돌았음·이미 실행 중)는 JobExecution 생성 전에 던져져 여기까지 오지 않으므로,
     * 여기 걸리는 건 실제 실행 실패뿐이다.
     */
    private void notifyIfFailed(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.FAILED) {
            return;
        }
        String jobName = jobExecution.getJobInstance().getJobName();
        String reasons = jobExecution.getAllFailureExceptions().stream()
            .map(Throwable::getMessage)
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));

        log.error("배치 실패 job={} params={}\n{}", jobName, jobExecution.getJobParameters(), reasons);
    }
}
