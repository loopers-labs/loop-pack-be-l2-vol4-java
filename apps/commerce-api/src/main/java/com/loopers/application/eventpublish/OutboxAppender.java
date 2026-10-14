package com.loopers.application.eventpublish;

import com.loopers.domain.eventpublish.OutboxMessage;
import com.loopers.domain.eventpublish.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox 저장을 도메인 이벤트 리스너 관점에서 조립하는 응용 컴포넌트.
 * 리스너 안에서 직접 OutboxService 를 부르지 않고 이 컴포넌트를 통해 이벤트별 직렬화를 격리한다.
 */
@RequiredArgsConstructor
@Component
public class OutboxAppender {

    private final OutboxService outboxService;
    private final EventPayloadSerializer serializer;

    public void append(
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String partitionKey,
        Object payload
    ) {
        String json = serializer.toJson(payload);
        outboxService.append(OutboxMessage.create(
            aggregateType, aggregateId, eventType, topic, partitionKey, json
        ));
    }
}
