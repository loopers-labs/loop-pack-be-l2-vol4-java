package com.loopers.batch.job.rank;

import java.util.List;

@FunctionalInterface
public interface RankMvReplacer {

    void replace(String periodKey, List<RankedRow> rows);
}