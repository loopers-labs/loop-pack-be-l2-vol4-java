package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * PENDING outbox 행을 Kafka 로 밀어내는 relay(@Scheduled 폴링).
 *
 * <p>행마다 broker ack 를 <b>기다린 뒤</b>({@code .get()}) PUBLISHED 로 마킹한다. 확인 전 마킹하면 발행 실패가
 * 유실이 되기 때문이다. 발행 예외는 행별로 잡아 해당 행만 PENDING 으로 남기고(다음 주기 재시도) 나머지는 진행한다
 * → <b>at-least-once</b>. 크래시로 "발행됨 + 마킹 전"이 되면 다음 주기에 재발행(중복)되며, 이 중복은 소비자
 * 멱등(event_handled, 결정 ④)이 흡수한다.</p>
 *
 * <p>배치를 한 트랜잭션으로 묶되 예외를 행별로 삼켜, 한 행의 실패가 다른 행의 PUBLISHED 마킹을 되돌리지 않게 한다
 * (마킹은 dirty checking 으로 커밋 시 일괄 flush). 다중 인스턴스 동시 relay 로 인한 중복 발행은 소비자 멱등이
 * 흡수하며, 분산락(ShedLock)은 nice-to-have 로 남긴다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final long PUBLISH_TIMEOUT_SECONDS = 5L;

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final KafkaTemplate<String, String> outboxKafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventJpaRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : pending) {
            try {
                outboxKafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("outbox relay 중단됨. eventId={}", event.getEventId());
                return;
            } catch (Exception e) {
                log.warn("outbox relay 발행 실패 — 다음 주기 재시도. eventId={}, topic={}",
                        event.getEventId(), event.getTopic(), e);
            }
        }
    }
}
