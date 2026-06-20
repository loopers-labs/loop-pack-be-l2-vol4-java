package com.loopers.application.like;

import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class LikeCountEventListener {

    private final ProductService productService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LikeCountChangedEvent event) {
        if (event.increase()) {
            productService.incrementLikeCount(event.productId());
        } else {
            productService.decrementLikeCount(event.productId());
        }
    }
}