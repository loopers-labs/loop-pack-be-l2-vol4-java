package com.loopers.batch.job.productrank.step;

import com.loopers.batch.job.productrank.ProductRankAggregationJobConfig;
import com.loopers.domain.ranking.AggregationTarget;
import com.loopers.domain.ranking.MvProductRankRepository;
import jakarta.annotation.Nonnull;
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

/**
 * 적재 전 해당 기간 키의 기존 MV 로우를 비운다.
 * 같은 파라미터로 다시 돌려도 결과가 같도록(멱등) 만드는 단계이며, 실패 후 재실행의 안전장치이기도 하다.
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MvPurgeTasklet implements Tasklet {

    private final MvProductRankRepository mvProductRankRepository;

    @Value("#{jobParameters['baseDate']}")
    private String baseDate;
    @Value("#{jobParameters['period']}")
    private String period;

    @Override
    public RepeatStatus execute(@Nonnull StepContribution contribution, @Nonnull ChunkContext chunkContext) {
        AggregationTarget target = AggregationTarget.of(baseDate, period);
        int deleted = mvProductRankRepository.deletePeriod(target.period(), target.periodKey());
        log.info("MV 초기화 — period={} key={} 삭제 {}건", target.period(), target.periodKey(), deleted);
        return RepeatStatus.FINISHED;
    }
}
