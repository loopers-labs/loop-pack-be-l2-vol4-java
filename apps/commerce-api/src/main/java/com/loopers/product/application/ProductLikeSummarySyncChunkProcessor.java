package com.loopers.product.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.OptionalLong;

@RequiredArgsConstructor
@Component
public class ProductLikeSummarySyncChunkProcessor {

    private static final String CHECKPOINT_NAME = "summary";

    private final ProductLikeSummarySyncRepository productLikeSummarySyncRepository;

    @Transactional
    public boolean syncNextChunk(int chunkSize) {
        long lastChangeId = productLikeSummarySyncRepository.lockCheckpoint(CHECKPOINT_NAME);
        OptionalLong maxChangeId = productLikeSummarySyncRepository.findNextChunkMaxChangeId(
            lastChangeId,
            chunkSize
        );
        if (maxChangeId.isEmpty()) {
            return false;
        }

        long currentMaxChangeId = maxChangeId.getAsLong();
        List<ProductLikeSummaryChange> changes = productLikeSummarySyncRepository.summarizeChanges(
            lastChangeId,
            currentMaxChangeId
        );
        productLikeSummarySyncRepository.applyChanges(changes);
        productLikeSummarySyncRepository.advanceCheckpoint(CHECKPOINT_NAME, currentMaxChangeId);
        return true;
    }
}
