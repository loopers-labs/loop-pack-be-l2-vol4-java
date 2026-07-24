package com.loopers.batch.job.ranking.step;

import com.loopers.ranking.application.ProductRankingCandidateRepository;
import com.loopers.ranking.application.RankingCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductRankingCandidateWriter implements ItemWriter<RankingCandidate> {

    private final ProductRankingCandidateRepository candidateRepository;

    @Override
    public void write(Chunk<? extends RankingCandidate> chunk) {
        candidateRepository.upsertAll(chunk.getItems());
    }
}
