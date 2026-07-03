package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 릴레이 — PENDING 이벤트를 폴링해 Kafka 로 발행하고 PUBLISHED 로 마킹한다.
 *
 * <h2>At Least Once</h2>
 * <p>발행 성공 후 마킹 전에 죽으면 재기동 시 같은 행을 다시 발행한다 — 유실은 없지만 중복은
 * 가능하다. 중복은 소비측의 멱등 처리(event_handled / 요청행 terminal 가드)가 흡수한다.
 *
 * <h2>처리량 — 파이프라인 발행 + 일괄 마킹 (10,000건 실측 벤치마크 기반 개선)</h2>
 * <p>초기 구현(건당 {@code send().get()} + 건당 마킹 커밋)은 건당 ~31ms, 초당 ~32건이 한계였다
 * (10,000건 = 5분 20초). 병목은 Kafka 가 아니라 <strong>건당 동기 왕복과 건당 커밋(fsync)</strong>:
 * <ul>
 *   <li>배치 내 전 건을 비동기로 send(파이프라인)한 뒤 ack 를 일괄 확인 — 왕복 횟수가 배치당 소수로 줄어든다.
 *       프로듀서의 {@code enable.idempotence=true} 가 파티션 내 전송 순서를 보존한다(in-flight 재시도 포함).</li>
 *   <li>PUBLISHED 마킹은 배치당 벌크 UPDATE 1회 — 커밋(fsync)이 건당 1회에서 배치당 1회로.</li>
 *   <li>틱당 비울 때까지 반복(drain) — 대기열이 쌓여도 다음 틱을 기다리지 않는다.</li>
 * </ul>
 *
 * <h2>순서 보장</h2>
 * <p>id 오름차순(기록 순서)으로 발행하고, ack 확인 시 <strong>첫 실패 이전 접두사만</strong> 마킹한 뒤
 * 중단해 다음 틱에 재시도한다. 실패 건을 건너뛰고 후속 건을 먼저 확정하면 같은 파티션 키의 순서가
 * 깨지기 때문. (파이프라인 중 부분 실패는 대부분 브로커 전체 장애라 접두사 처리로 충분하며,
 * 드물게 후속 건이 이미 브로커에 들어간 경우의 재전송 중복은 소비측 멱등이 흡수한다.)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 500;
    /** 틱당 드레인 상한 — 폭주 시에도 한 틱이 무한정 돌지 않도록 안전판. */
    private static final int MAX_BATCHES_PER_TICK = 50;
    private static final long SEND_TIMEOUT_SECONDS = 10L;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1_000)
    public void relay() {
        for (int i = 0; i < MAX_BATCHES_PER_TICK; i++) {
            List<OutboxEvent> pending = outboxEventRepository.findPending(BATCH_SIZE);
            if (pending.isEmpty()) {
                return;
            }
            int confirmed = relayBatch(pending);
            if (confirmed < pending.size()) {
                return;   // 실패 지점부터 다음 틱 재시도 (순서 보장)
            }
            if (pending.size() < BATCH_SIZE) {
                return;   // 대기열 소진
            }
        }
    }

    /**
     * 배치 파이프라인 발행: 전 건 비동기 send → ack 일괄 확인 → 성공 접두사만 벌크 마킹.
     *
     * @return 발행 확정(마킹)된 건수
     */
    private int relayBatch(List<OutboxEvent> batch) {
        // payload 는 EventEnvelope 직렬화 전문 — JsonNode 로 되읽어 보내야 JsonSerializer 가
        // 문자열 재인용(이중 인코딩) 없이 원문 JSON 그대로 전송한다.
        List<CompletableFuture<?>> futures = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            try {
                futures.add(kafkaTemplate.send(event.getTopic(), event.getPartitionKey(),
                    objectMapper.readTree(event.getPayload())));
            } catch (Exception e) {
                log.warn("[OutboxRelay] 발행 준비 실패 — 이 지점부터 다음 틱 재시도. eventId={}", event.getEventId(), e);
                break;
            }
        }

        int confirmed = 0;
        for (CompletableFuture<?> future : futures) {
            try {
                future.get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                confirmed++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[OutboxRelay] 발행 대기 중단 — 다음 틱 재시도. confirmed={}", confirmed);
                break;
            } catch (Exception e) {
                log.warn("[OutboxRelay] 발행 실패 — 접두사 {}건만 확정, 다음 틱 재시도. eventId={}",
                    confirmed, batch.get(confirmed).getEventId(), e);
                break;
            }
        }

        if (confirmed > 0) {
            outboxEventRepository.markPublished(
                batch.subList(0, confirmed).stream().map(OutboxEvent::getId).toList());
        }
        return confirmed;
    }
}
