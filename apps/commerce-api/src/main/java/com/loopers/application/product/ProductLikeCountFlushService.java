package com.loopers.application.product;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductLikeCountFlushService {
    private static final int DEFAULT_FLUSH_LIMIT = 500;

    private final ProductLikeCountRepository productLikeCountRepository;
    private final ProductService productService;

    @Transactional
    public void flushDirtyLikeCounts() {
        productLikeCountRepository.getDirtyCounts(DEFAULT_FLUSH_LIMIT)
            .forEach(this::flush);
    }

    private void flush(ProductLikeCountSnapshot snapshot) {
        productService.updateLikeCountSnapshot(snapshot.productId(), snapshot.likeCount());
        productLikeCountRepository.clearDirty(snapshot.productId());
    }
}
