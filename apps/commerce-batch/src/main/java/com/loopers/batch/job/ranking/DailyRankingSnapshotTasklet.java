package com.loopers.batch.job.ranking;

import com.loopers.ranking.RankingScoreFormula;
import com.loopers.ranking.DailyRankingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = DailyRankingSnapshotJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class DailyRankingSnapshotTasklet implements Tasklet {

    private final DailyRankingMetricReader metricReader;
    private final DailyRankingSnapshotPublisher snapshotPublisher;
    private final DailyRankingExecutionLock executionLock;

    @Value("#{jobParameters['requestDate']}")
    private String requestDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate date = parseRequestDate();
        long jobExecutionId = chunkContext.getStepContext().getStepExecution().getJobExecutionId();
        if (!executionLock.acquire(date, jobExecutionId)) {
            throw new IllegalStateException("같은 날짜의 랭킹 스냅샷 Job이 이미 실행 중입니다.");
        }

        try {
            var scores = metricReader.read(date).stream()
                .map(metric -> new DailyRankingSnapshotPublisher.DailyRankingScore(
                    metric.productId(),
                    RankingScoreFormula.calculate(metric.viewCount(), metric.likeCount(), metric.salesCount())
                ))
                .toList();

            snapshotPublisher.publish(date, jobExecutionId, scores);
            return RepeatStatus.FINISHED;
        } finally {
            executionLock.release(date, jobExecutionId);
        }
    }

    private LocalDate parseRequestDate() {
        if (requestDate == null || requestDate.isBlank()) {
            throw new IllegalArgumentException("requestDate job parameter는 필수입니다.");
        }
        try {
            LocalDate date = LocalDate.parse(requestDate);
            if (!date.isBefore(LocalDate.now(DailyRankingKey.ZONE_ID))) {
                throw new IllegalArgumentException("requestDate는 한국 시간 기준 종료된 날짜만 허용합니다.");
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("requestDate는 yyyy-MM-dd 형식이어야 합니다.", exception);
        }
    }
}
