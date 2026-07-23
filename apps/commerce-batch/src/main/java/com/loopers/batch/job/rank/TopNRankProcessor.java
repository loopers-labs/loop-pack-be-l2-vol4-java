package com.loopers.batch.job.rank;

import com.loopers.domain.rank.RankScorePolicy;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 집계 결과 스트림을 순위로 환산하는 Chunk Processor.
 *
 * <p>Reader가 이미 가중 점수 내림차순으로 정렬해 전체 상품을 흘려보내면, 읽은 순서대로 rank를 부여하고
 * 점수를 계산해 MV 엔티티를 만든다. rank가 TOP N을 넘으면 {@code null}을 반환해 청크 파이프라인에서
 * 걸러지므로(Writer에 도달하지 않음) MV에는 상위 N개만 적재된다.
 *
 * <p>rank 카운터는 상태를 가지므로 이 Processor는 {@code @StepScope}로 스텝 실행마다 새로 생성해야 하며,
 * 단일 스레드 스텝(기본)에서만 읽은 순서 = rank가 보장된다.
 */
public class TopNRankProcessor<T> implements ItemProcessor<ProductMetricsAggregate, T> {

    private final int topN;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final RankEntityFactory<T> factory;
    private final AtomicInteger assignedRank = new AtomicInteger(0);

    public TopNRankProcessor(int topN, LocalDate periodStart, LocalDate periodEnd, RankEntityFactory<T> factory) {
        this.topN = topN;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.factory = factory;
    }

    @Nullable
    @Override
    public T process(ProductMetricsAggregate aggregate) {
        int rank = assignedRank.incrementAndGet();
        if (rank > topN) {
            return null; // TOP N 밖 — 청크에서 제외되어 Writer에 도달하지 않는다
        }
        double score = RankScorePolicy.score(aggregate.viewSum(), aggregate.likeSum(), aggregate.salesSum());
        return factory.create(rank, aggregate.productId(), score, periodStart, periodEnd);
    }
}
