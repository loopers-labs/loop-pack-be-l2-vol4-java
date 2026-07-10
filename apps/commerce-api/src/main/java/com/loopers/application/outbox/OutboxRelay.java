package com.loopers.application.outbox;

import com.loopers.domain.outbox.OutboxEventHandler;
import com.loopers.domain.outbox.OutboxService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// ③ 릴레이: PENDING outbox를 주기적으로 재처리한다 — at-least-once의 실제 보증자.
// eventType으로 핸들러를 찾아 위임하며, 개별 실패는 삼켜 다음 주기에 다시 시도한다.
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxRelay {

    private final OutboxService outboxService;
    private final List<OutboxEventHandler> handlers;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        outboxService.findPending().forEach(outbox -> {
            try {
                handlers.stream()
                        .filter(handler -> handler.supports(outbox.getEventType()))
                        .findFirst()
                        .orElseThrow(() -> new CoreException(
                                ErrorType.INTERNAL_ERROR, "처리할 핸들러가 없습니다. eventType=" + outbox.getEventType()))
                        .handle(outbox);
            } catch (Exception e) {
                log.error("outbox 반영 실패. eventId={}, eventType={}", outbox.getEventId(), outbox.getEventType(), e);
            }
        });
    }
}
