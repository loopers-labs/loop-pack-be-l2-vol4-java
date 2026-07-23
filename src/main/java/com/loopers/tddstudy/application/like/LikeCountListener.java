package com.loopers.tddstudy.application.like;

import com.loopers.tddstudy.domain.like.event.ProductLikedEvent;
import com.loopers.tddstudy.domain.like.event.ProductUnlikedEvent;
import com.loopers.tddstudy.domain.product.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LikeCountListener {

    private final ProductRepository productRepository;

    public LikeCountListener(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLiked(ProductLikedEvent event) {
        productRepository.findByIdWithLock(event.productId()).ifPresent(p -> {
            p.increaseLikeCount();
            productRepository.save(p);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUnliked(ProductUnlikedEvent event) {
        productRepository.findByIdWithLock(event.productId()).ifPresent(p -> {
            p.decreaseLikeCount();
            productRepository.save(p);
        });
    }
}
