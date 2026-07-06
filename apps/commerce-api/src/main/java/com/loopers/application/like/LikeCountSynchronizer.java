package com.loopers.application.like;

import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * like_count 집계 반영 — 커밋 이후 새 트랜잭션 경계.
 *
 * <p>{@link LikeCountEventListener}(AFTER_COMMIT)는 활성 트랜잭션이 없는 시점에 실행되므로,
 * 벌크 UPDATE 실행을 위해 {@link Propagation#REQUIRES_NEW}로 새 트랜잭션을 연다.
 * 예외를 삼키는 try/catch 를 트랜잭션 밖(리스너)에 두기 위해 별도 빈으로 분리했다
 * — 같은 빈 내부 호출은 프록시를 타지 않아 {@code @Transactional} 이 적용되지 않는다.
 */
@RequiredArgsConstructor
@Component
public class LikeCountSynchronizer {

    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void apply(LikeChangedEvent event) {
        switch (event.type()) {
            case LIKED -> productRepository.increaseLikeCount(event.productId());
            case UNLIKED -> productRepository.decreaseLikeCount(event.productId());
        }
    }
}
