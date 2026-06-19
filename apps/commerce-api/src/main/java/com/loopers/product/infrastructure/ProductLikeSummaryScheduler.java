package com.loopers.product.infrastructure;

import com.loopers.product.application.ProductLikeSummarySynchronizer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
    name = "commerce.product-like-summary.sync-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ProductLikeSummaryScheduler {

    private final ProductLikeSummarySynchronizer productLikeSummarySynchronizer;

    @Scheduled(fixedDelayString = "${commerce.product-like-summary.sync-fixed-delay-ms:5000}")
    public void sync() {
        productLikeSummarySynchronizer.sync();
    }
}
