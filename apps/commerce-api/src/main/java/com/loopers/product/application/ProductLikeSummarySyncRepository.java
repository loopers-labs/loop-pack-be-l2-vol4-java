package com.loopers.product.application;

import java.util.List;
import java.util.OptionalLong;

public interface ProductLikeSummarySyncRepository {

    long lockCheckpoint(String checkpointName);

    OptionalLong findNextChunkMaxChangeId(long lastChangeId, int chunkSize);

    List<ProductLikeSummaryChange> summarizeChanges(long lastChangeId, long maxChangeId);

    void applyChanges(List<ProductLikeSummaryChange> changes);

    void advanceCheckpoint(String checkpointName, long lastChangeId);
}
