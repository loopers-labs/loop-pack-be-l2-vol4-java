package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@link LikeChangedEvent}를 좋아요 트랜잭션 <b>커밋 이후</b>에 Kafka로 발행한다.
 * 도메인(LikeService)이 Kafka를 모르도록 카프카 관심사를 인프라로 격리한 어댑터.
 *
 * <p><b>발행 시점(AFTER_COMMIT)</b>: 좋아요 행이 롤백되면 카운터 이벤트도 나가지 않도록 커밋 이후에만
 * 보낸다. 단 "커밋은 됐는데 send 실패" 시 카운터 이벤트가 유실될 수 있다(at-most-once 구간).
 * 이는 reconcile 스케줄러(진실원천 product_like COUNT 재계산)가 주기적으로 교정한다.
 * 더 강한 보장이 필요하면 Transactional Outbox로 승급 — 현 단계 범위 밖.
 *
 * <p><b>key=productId</b>: 같은 상품의 이벤트가 같은 파티션으로 가서 같은 컨슈머가 순서대로 처리한다.
 * 덕분에 컨슈머가 상품별 델타를 안전하게 합산(coalescing)해 UPDATE를 1회로 합칠 수 있다.
 */
@Component
@RequiredArgsConstructor
public class LikeEventKafkaPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${like-events.topic}")
    private String topic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(LikeChangedEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.productId()), event);
    }
}
