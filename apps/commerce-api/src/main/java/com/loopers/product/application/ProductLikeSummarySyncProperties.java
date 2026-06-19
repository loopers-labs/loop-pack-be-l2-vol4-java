package com.loopers.product.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("commerce.product-like-summary")
public record ProductLikeSummarySyncProperties(
    @DefaultValue("1000") int syncChunkSize,
    @DefaultValue("3") int syncMaxChunksPerRun
) {

    public ProductLikeSummarySyncProperties {
        if (syncChunkSize <= 0) {
            throw new IllegalArgumentException("syncChunkSize는 1 이상이어야 합니다.");
        }
        if (syncMaxChunksPerRun <= 0) {
            throw new IllegalArgumentException("syncMaxChunksPerRun은 1 이상이어야 합니다.");
        }
    }
}
