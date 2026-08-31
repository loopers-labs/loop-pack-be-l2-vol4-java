package com.loopers.batch.job.ranking;

import org.springframework.batch.item.ItemProcessor;

// Hides: the deterministic fail-before-third incident seam used to prove restart behavior.
public class RankingItemProcessor implements ItemProcessor<RankingItemReader.SourceRow, RankingItemReader.SourceRow> {
    private final boolean injectFailure;

    public RankingItemProcessor(boolean injectFailure) {
        this.injectFailure = injectFailure;
    }

    @Override
    public RankingItemReader.SourceRow process(RankingItemReader.SourceRow item) {
        if (injectFailure && item.seq() == 3) {
            throw new IllegalStateException("controlled failure before third item");
        }
        return item;
    }
}
