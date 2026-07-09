package com.loopers.application.like;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class LikeApplicationService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 좋아요 등록(주요 로직)만 트랜잭션에 둔다. 집계(likeCount)는 부가 로직으로 보고
     * {@link ProductLikedEvent} 로 분리해 커밋 후(AFTER_COMMIT) 별도 트랜잭션에서 반영한다
     * → 집계 실패가 좋아요 등록을 롤백하지 않는다(eventual consistency). 새로 등록(affected==1)된 경우에만 발행.
     */
    @Transactional
    public void register(Long userId, Long productId) {
        if (productRepository.find(productId).isEmpty()) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
        if (likeRepository.save(Like.create(userId, productId)) == 1) {
            eventPublisher.publishEvent(new ProductLikedEvent(userId, productId, ZonedDateTime.now()));
        }
    }

    @Transactional
    public void cancel(Long userId, Long productId) {
        if (likeRepository.delete(userId, productId) == 1) {
            eventPublisher.publishEvent(new ProductUnlikedEvent(userId, productId, ZonedDateTime.now()));
        }
    }

    @Transactional(readOnly = true)
    public PageResult<LikeInfo> getMyLikes(Long userId, int page, int size) {
        return likeRepository.findAllByUserId(userId, page, size).map(LikeInfo::from);
    }
}
