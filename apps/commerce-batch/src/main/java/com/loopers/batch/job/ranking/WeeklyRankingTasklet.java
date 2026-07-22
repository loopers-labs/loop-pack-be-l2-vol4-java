package com.loopers.batch.job.ranking;

import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
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
 * 요청 하루(requestDate)가 속한 ISO 주를 집계해 주간 랭킹 MV 를 재적재한다(D3: Tasklet + 집계 SQL 한 방).
 * 그 주 전체 DELETE → TOP 100 INSERT 를 Step 의 트랜잭션 경계 안에서 원자적으로 수행해 멱등을 보장한다(D7).
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class WeeklyRankingTasklet implements Tasklet {

    private final MvProductRankWeeklyJpaRepository mvRepository;

    @Value("#{jobParameters['requestDate']}")
    private LocalDate requestDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        RankingWeek week = RankingWeek.from(requestDate);
        mvRepository.deleteByYearWeek(week.yearWeek());
        mvRepository.insertWeeklyTop100(week.yearWeek(), week.startDate(), week.endDate());
        log.info("주간 랭킹 MV 재적재 완료: yearWeek={}, {} ~ {}", week.yearWeek(), week.startDate(), week.endDate());
        return RepeatStatus.FINISHED;
    }
}
