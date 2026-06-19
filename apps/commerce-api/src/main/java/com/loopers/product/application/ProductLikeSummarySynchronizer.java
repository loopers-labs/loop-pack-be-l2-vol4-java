package com.loopers.product.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeSummarySynchronizer {

    private final ProductLikeSummarySyncChunkProcessor productLikeSummarySyncChunkProcessor;
    private final ProductLikeSummarySyncProperties productLikeSummarySyncProperties;

    public void sync() {
        for (int i = 0; i < productLikeSummarySyncProperties.syncMaxChunksPerRun(); i++) {
            boolean synced = productLikeSummarySyncChunkProcessor.syncNextChunk(
                productLikeSummarySyncProperties.syncChunkSize()
            );
            if (!synced) {
                return;
            }
        }
    }
}
