package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.product.event.ProductViewedEvent;
import com.loopers.interfaces.api.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * 상품 조회 이벤트를 catalog-events 로 <b>직접 발행</b>하는 어댑터(outbox 를 거치지 않는다).
 *
 * <p><b>왜 outbox 가 아닌가</b>: 조회는 읽기라 DB 상태 변경이 없다 → outbox 의 존재 이유(상태변경과 이벤트의 원자성)에
 * 묶을 대상이 없다. 게다가 상세 조회의 캐시 히트 경로는 커넥션 선점을 피하려 트랜잭션을 열지 않는다(no-TX). 조회수는
 * 유실을 허용하는 분석 지표라, 내구성 대신 직접 발행(fire-and-forget)으로 충분하다.</p>
 *
 * <p><b>fallbackExecution=true</b>: 캐시 히트(no-TX)·미스(각 repo 개별 TX) 어느 경로든 발행 지점에 활성 트랜잭션이
 * 없을 수 있다. fallback 을 켜 트랜잭션이 없으면 즉시 실행되게 한다. 발행 실패는 삼켜 로깅만 한다 — 조회 응답이
 * 분석 신호 전파 실패로 막히면 안 되기 때문(유실 허용의 실현). broker→consumer 재전달 중복은 event_handled 가 흡수한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewedEventPublisher {

    private final KafkaTemplate<String, String> outboxKafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductViewed(ProductViewedEvent event) {
        CatalogEventMessage message = new CatalogEventMessage(
                UUID.randomUUID().toString(), CatalogEventType.PRODUCT_VIEWED,
                event.productId(), event.userId(), event.occurredAt());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message); // 직렬화는 동기 — 여기서 실패하면 발행 자체를 건너뛴다.
        } catch (JsonProcessingException e) {
            log.warn("조회 이벤트 직렬화 실패 — 유실 허용하고 넘어간다. productId={}", event.productId(), e);
            return;
        }

        // send() 는 비동기라 broker ack 실패는 반환 future 로만 온다(try-catch 로는 못 잡음).
        // 유실을 허용하되 "실패는 로깅한다"는 의도를 정확히 지키려면 whenComplete 로 완료 콜백에서 로깅해야 한다.
        outboxKafkaTemplate.send(KafkaTopicConfig.CATALOG_EVENTS, String.valueOf(event.productId()), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("조회 이벤트 발행 실패 — 유실 허용하고 넘어간다. productId={}", event.productId(), ex);
                    }
                });
    }
}
