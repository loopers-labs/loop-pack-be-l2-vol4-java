package com.loopers.like.application;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeErrorCode;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.application.ProductReader;
import com.loopers.product.application.event.ProductLikeChangedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductReader productReader;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void register(Long userId, Long productId) {
        productReader.ensureActiveExists(productId);
        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresentOrElse(
                        existing -> {
                            if (existing.getDeletedAt() != null) {
                                existing.restore();
                                publishChanged(productId, 1L);
                            }
                        },
                        () -> {
                            saveNewLike(userId, productId);
                            publishChanged(productId, 1L);
                        }
                );
    }

    private void saveNewLike(Long userId, Long productId) {
        try {
            likeRepository.save(Like.create(userId, productId));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, LikeErrorCode.ALREADY_LIKED);
        }
    }

    @Transactional
    public void cancel(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(existing -> {
                    if (existing.getDeletedAt() == null) {
                        existing.delete();
                        publishChanged(productId, -1L);
                    }
                });
    }

    private void publishChanged(Long productId, long delta) {
        eventPublisher.publishEvent(new ProductLikeChangedEvent(productId, delta));
    }
}
