package com.loopers.batch.job.productrank.step;

import com.loopers.batch.job.productrank.ProductRankAggregationJobConfig;
import com.loopers.domain.ranking.AggregationTarget;
import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.RankingProperties;
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

import java.util.List;

/**
 * 순위 확정 단계 — 적재된 전체 후보를 점수 내림차순으로 정렬해 상위 N 개에만 순위를 부여하고 나머지는 버린다.
 *
 * <p>이 일을 청크 단계에서 못 하는 이유: 랭킹은 "전체를 본 뒤 상위 N"이라 스트리밍 중에는 확정할 수 없다.
 * 그래서 청크 단계는 전 상품을 rank_no=null 로 적재만 하고, 정렬·절단은 집합 연산으로 여기서 끝낸다.
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MvRankTasklet implements Tasklet {

    private final MvProductRankRepository mvProductRankRepository;
    private final RankingProperties rankingProperties;

    @Value("#{jobParameters['baseDate']}")
    private String baseDate;
    @Value("#{jobParameters['period']}")
    private String period;

    @Override
    public RepeatStatus execute(@Nonnull StepContribution contribution, @Nonnull ChunkContext chunkContext) {
        AggregationTarget target = AggregationTarget.of(baseDate, period);

        List<Long> topProductIds = mvProductRankRepository.findTopProductIds(
            target.period(), target.periodKey(), rankingProperties.topN());
        mvProductRankRepository.assignRanks(target.period(), target.periodKey(), topProductIds);
        int removed = mvProductRankRepository.deleteUnranked(target.period(), target.periodKey());

        log.info("랭킹 확정 — period={} key={} TOP {}건 유지, {}건 제거",
            target.period(), target.periodKey(), topProductIds.size(), removed);
        return RepeatStatus.FINISHED;
    }
}
