package com.loopers.application.product;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@ConditionalOnProperty(
    prefix = "loopers.product-like-count",
    name = "flush-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ProductLikeCountFlushScheduler {

    private final ProductLikeCountFlushService productLikeCountFlushService;

    @Scheduled(fixedDelayString = "${loopers.product-like-count.flush-delay:5000}")
    public void flushDirtyLikeCounts() {
        productLikeCountFlushService.flushDirtyLikeCounts();
    }
}
