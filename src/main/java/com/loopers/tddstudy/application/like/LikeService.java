package com.loopers.tddstudy.application.like;

import com.loopers.tddstudy.domain.like.Like;
import com.loopers.tddstudy.domain.like.LikeRepository;
import com.loopers.tddstudy.domain.like.event.ProductLikedEvent;
import com.loopers.tddstudy.domain.like.event.ProductUnlikedEvent;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.loopers.tddstudy.application.log.UserActionEvent;

@Service
@Transactional
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    public LikeService(LikeRepository likeRepository,
                       ProductRepository productRepository,
                       ApplicationEventPublisher eventPublisher) {
        this.likeRepository = likeRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @CacheEvict(cacheNames = {"product", "products"}, allEntries = true)
    public void addLike(Long userId, Long productId) {
        // 존재 검증만 (집계는 리스너로 분리)
        productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        // 멱등: 이미 좋아요면 저장/발행 모두 안 함
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }

        likeRepository.save(new Like(userId, productId));
        eventPublisher.publishEvent(new ProductLikedEvent(productId, userId));
        eventPublisher.publishEvent(new UserActionEvent(userId, "LIKE", productId));
    }

    @CacheEvict(cacheNames = {"product", "products"}, allEntries = true)
    public void cancelLike(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId).ifPresent(like -> {
            likeRepository.delete(like);
            eventPublisher.publishEvent(new ProductUnlikedEvent(productId, userId));
        });
    }

    @Transactional(readOnly = true)
    public List<Long> getMyLikes(Long userId) {
        return likeRepository.findAllByUserId(userId).stream()
                .map(Like::getProductId)
                .collect(Collectors.toList());
    }
}
