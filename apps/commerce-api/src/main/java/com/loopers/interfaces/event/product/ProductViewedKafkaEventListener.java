package com.loopers.interfaces.event.product;

import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.infrastructure.kafka.KafkaEventEnvelopeSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 조회수는 outbox를 타지 않는다 - 캐시 우선 조회 경로(ProductFacade.getProduct)는 트랜잭션이 없어
// BEFORE_COMMIT 기반 OutboxRecordEventListener가 발동하지 않기 때문이다(억지로 트랜잭션을 씌우면
// 캐시 히트 경로까지 매번 DB write가 생겨 핫패스가 망가진다). best-effort(유실 허용, C등급)로
// UserActivityLogListener와 동일한 AFTER_COMMIT + fallbackExecution 패턴을 사용해 트랜잭션 유무와 무관하게 실행한다.
// 목록 조회(ProductListViewedEvent)는 상품 단위 키가 없어 제외한다 - 로깅만 유지된다.
@Slf4j
@RequiredArgsConstructor
@Component
public class ProductViewedKafkaEventListener {

    private static final String TOPIC = "catalog-events";

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final KafkaEventEnvelopeSerializer envelopeSerializer;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(ProductViewedEvent event) {
        try {
            String envelope = envelopeSerializer.serialize(
                    event.eventId(), event.aggregateType(), event.aggregateId(), event.eventType(), event);
            stringKafkaTemplate.send(TOPIC, event.aggregateId(), envelope);
        } catch (Exception e) {
            log.warn("상품 조회 이벤트 발행 실패(best-effort, 재시도 없음). productId={}", event.productId(), e);
        }
    }
}
