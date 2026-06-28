package com.loopers.application.like;

import com.loopers.domain.like.event.LikeChangedEvent;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LikeEventHandler {

    private final ProductService productService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(LikeChangedEvent event) {
        switch (event.type()) {
            case LIKED -> productService.incrementLikeCount(event.productId());
            case UNLIKED -> productService.decrementLikeCount(event.productId());
        }
    }
}
