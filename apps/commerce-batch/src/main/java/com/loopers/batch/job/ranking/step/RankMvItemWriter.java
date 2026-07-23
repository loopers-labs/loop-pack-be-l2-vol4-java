package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.RankAggregationJobConfig;
import com.loopers.ranking.domain.ProductRankModel;
import com.loopers.ranking.domain.RankPeriod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 대상 MV를 비우고(clear) 새 rank를 적재(insert)하는 일을 <b>하나의 청크 트랜잭션</b>에서 수행한다.
 * clear를 별도 Step으로 분리하면 aggregate가 실패했을 때 MV가 다음 성공까지 무기한 비므로,
 * 비우기+적재를 한 트랜잭션에 묶어 실패 시 이전 판이 그대로 살아남게 한다(fail = keep old).
 * 또 DELETE가 커밋 전엔 다른 트랜잭션에 보이지 않아, 조회가 빈/부분 상태의 MV를 볼 창도 없앤다.
 * <p>
 * 전제: CHUNK_SIZE >= TOP_N 이라 단일 청크(단일 트랜잭션)로 clear+insert가 원자적으로 커밋된다.
 * 소스(product_metrics)가 비면 write가 호출되지 않아 clear도 일어나지 않는다 — 새 판이 없으면
 * 마지막 정상 판을 유지한다(의도된 동작).
 */
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankAggregationJobConfig.JOB_NAME)
@Component
public class RankMvItemWriter implements ItemWriter<ProductRankModel> {

    @PersistenceContext
    private EntityManager entityManager;

    private final RankPeriod period;
    private boolean cleared = false;

    public RankMvItemWriter(@Value("#{jobParameters['period']}") String period) {
        this.period = RankPeriod.from(period);
    }

    @Override
    public void write(Chunk<? extends ProductRankModel> chunk) {
        if (!cleared) {
            entityManager.createQuery("DELETE FROM " + period.targetEntityName()).executeUpdate();
            cleared = true;
        }
        for (ProductRankModel item : chunk) {
            entityManager.persist(item);
        }
    }
}
