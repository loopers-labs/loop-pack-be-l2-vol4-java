package com.loopers.application.like;

import com.loopers.domain.like.LikeEventType;
import com.loopers.domain.like.LikeOutboxService;
import com.loopers.domain.product.ProductStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeOutboxEventListener {

    private final ProductStatsService productStatsService;
    private final LikeOutboxService likeOutboxService;

    // markDoneIfPending과 increase/decreaseLikeCount를 같은 트랜잭션으로 묶어 멱등성을 보장한다.
    // 중복 이벤트가 발행돼도 markDoneIfPending이 false를 반환하면 조기 종료한다.
    @EventListener
    @Transactional
    public void handle(LikeCountChangedEvent event) {
        if (!likeOutboxService.markDoneIfPending(event.outboxId())) {
            return;
        }
        if (event.eventType() == LikeEventType.LIKED_EVENT) {
            productStatsService.increaseLikeCount(event.productId());
        } else if (event.eventType() == LikeEventType.UNLIKED_EVENT) {
            productStatsService.decreaseLikeCount(event.productId());
        }
    }
}
