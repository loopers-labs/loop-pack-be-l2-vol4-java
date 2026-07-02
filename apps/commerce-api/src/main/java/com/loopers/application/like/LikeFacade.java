package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void like(String loginId, Long productId) {
        Long userId = resolveUserId(loginId);
        if (likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 이미 좋아요한 경우
        }
        productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        likeRepository.save(new Like(userId, productId));
        eventPublisher.publishEvent(new LikeAdded(userId, productId, ZonedDateTime.now()));
    }

    @Transactional
    public void unlike(String loginId, Long productId) {
        Long userId = resolveUserId(loginId);
        if (!likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 좋아요하지 않은 경우
        }
        likeRepository.deleteBy(userId, productId);
        eventPublisher.publishEvent(new LikeRemoved(userId, productId, ZonedDateTime.now()));
    }

    private Long resolveUserId(String loginId) {
        return userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."))
            .getId();
    }
}
