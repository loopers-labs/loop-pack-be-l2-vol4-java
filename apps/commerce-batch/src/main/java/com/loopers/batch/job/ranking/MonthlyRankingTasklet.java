package com.loopers.batch.job.ranking;

import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 요청 하루(requestDate)가 속한 달을 집계해 월간 랭킹 MV 를 재적재한다(주간과 동일 구조, 버킷만 달).
 * 그 달 전체 DELETE → TOP 100 INSERT 를 Step 트랜잭션 안에서 원자적으로 수행해 멱등을 보장한다(D7).
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyRankingTasklet implements Tasklet {

    private final MvProductRankMonthlyJpaRepository mvRepository;

    @Value("#{jobParameters['requestDate']}")
    private LocalDate requestDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        RankingMonth month = RankingMonth.from(requestDate);
        mvRepository.deleteByYearMonth(month.yearMonth());
        mvRepository.insertMonthlyTop100(month.yearMonth(), month.startDate(), month.endDate());
        log.info("월간 랭킹 MV 재적재 완료: yearMonth={}, {} ~ {}", month.yearMonth(), month.startDate(), month.endDate());
        return RepeatStatus.FINISHED;
    }
}
