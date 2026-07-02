package com.loopers.application.like;

import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.event.LikeAdded;
import com.loopers.domain.like.event.LikeRemoved;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeCountListener {

    private final LikeCountRepository likeCountRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeAdded(LikeAdded event) {
        try {
            likeCountRepository.increase(event.productId());
        } catch (Exception ex) {
            // fire-and-forget: 좋아요는 이미 커밋됨. 드리프트는 알려진 갭(Step 2에서 닫음).
            log.warn("likeCount 증가 실패 productId={}", event.productId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeRemoved(LikeRemoved event) {
        try {
            likeCountRepository.decrease(event.productId());
        } catch (Exception ex) {
            log.warn("likeCount 감소 실패 productId={}", event.productId(), ex);
        }
    }
}
