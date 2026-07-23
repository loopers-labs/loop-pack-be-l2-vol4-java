package com.loopers.batch.job.rank.step;

import com.loopers.batch.job.rank.RankMvReplacer;
import com.loopers.batch.job.rank.RankedRow;
import com.loopers.domain.rank.ProductRankScoreModel;
import com.loopers.infrastructure.rank.ProductRankScoreJpaRepository;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * staging 에서 기간 상위 100 을 점수 내림차순으로 뽑아 rank_no 를 매기고, 대상 MV 를 통째로 교체(period_key 삭제 후 삽입)한 뒤 staging 을 비운다.
 * MV 교체가 delete-by-key + insert 라 같은 기간 재실행에도 멱등하다.
 */
public class RankTopNTasklet implements Tasklet {

    private final String periodKey;
    private final ProductRankScoreJpaRepository scoreRepository;
    private final RankMvReplacer mvReplacer;

    public RankTopNTasklet(String periodKey, ProductRankScoreJpaRepository scoreRepository, RankMvReplacer mvReplacer) {
        this.periodKey = periodKey;
        this.scoreRepository = scoreRepository;
        this.mvReplacer = mvReplacer;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<ProductRankScoreModel> top = scoreRepository.findTop100ByPeriodKeyOrderByScoreDesc(periodKey);

        List<RankedRow> rows = new ArrayList<>(top.size());
        int rankNo = 1;
        for (ProductRankScoreModel score : top) {
            rows.add(new RankedRow(rankNo++, score.getProductId(), score.getScore()));
        }

        mvReplacer.replace(periodKey, rows);
        scoreRepository.deleteByPeriodKey(periodKey);
        return RepeatStatus.FINISHED;
    }
}