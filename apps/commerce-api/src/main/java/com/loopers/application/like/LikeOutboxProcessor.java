package com.loopers.application.like;

import com.loopers.domain.like.LikeOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeOutboxProcessor {

    private final LikeOutboxService likeOutboxService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 1000)
    public void process() {
        likeOutboxService.findPending().forEach(outbox -> {
            try {
                eventPublisher.publishEvent(LikeCountChangedEvent.from(outbox));
            } catch (Exception e) {
                log.error("outbox 이벤트 발행 실패. id={}, eventType={}", outbox.getId(), outbox.getEventType(), e);
            }
        });
    }
}
