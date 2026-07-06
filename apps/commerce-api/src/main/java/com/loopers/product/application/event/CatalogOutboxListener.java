package com.loopers.product.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.outbox.domain.OutboxEvent;
import com.loopers.outbox.domain.OutboxEventRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 변경 이벤트를 catalog-events outbox 에 적재한다.
 * BEFORE_COMMIT: 좋아요 트랜잭션과 같은 트랜잭션으로 묶여 원자적으로 기록된다(릴레이가 발행).
 */
@Component
@RequiredArgsConstructor
public class CatalogOutboxListener {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(ProductLikeChangedEvent event) {
        CatalogEventMessage message = CatalogEventMessage.like(event.productId(), event.delta());
        outboxEventRepository.save(OutboxEvent.pending(
                KafkaTopic.CATALOG_EVENTS,
                String.valueOf(event.productId()), // 파티션 key = productId → 좋아요/취소 순서 보장
                "CatalogLike",
                serialize(message)
        ));
    }

    private String serialize(CatalogEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "CatalogEventMessage 직렬화 실패");
        }
    }
}
