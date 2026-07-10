package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 입장 스케줄러 — interval 마다 맨 앞 batch 에 토큰을 발급하고(SET 먼저) 대기열에서 제거한다(ZREM 나중).
 * 산정(Hikari 풀 40 기준): 이론 최대 200 TPS × 안전마진 70% = 140 TPS —
 * 1초 140명 일괄이 아니라 100ms × 14명으로 나눠 흘려보내 Thundering Herd 를 완화한다.
 *
 * 다중 인스턴스 미고려 — 기존 스케줄러 3종(relay/reconcile/rank)과 동일하게 단일 인스턴스 전제.
 * (peek→SET→ZREM 은 원자적이지 않아, 다중 인스턴스면 같은 배치를 중복 peek 해 중복 발급된다.)
 *
 * 게이트 스위치(queue.order-gate.enabled)와 수명을 함께 묶는다 — 행사 때만 대기열을 운영하고,
 * 게이트 off 인 평시·기존 테스트 컨텍스트에서는 스케줄러가 순번을 건드리지 않는다.
 */
@ConditionalOnProperty(name = "queue.order-gate.enabled", havingValue = "true")
@RequiredArgsConstructor
@Component
public class QueueAdmissionScheduler {

    private static final Logger log = LoggerFactory.getLogger(QueueAdmissionScheduler.class);

    private final QueueFacade queueFacade;

    @Scheduled(fixedDelayString = "${queue.admission.interval-ms:100}")
    public void admit() {
        try {
            queueFacade.admitNextBatch();
        } catch (Exception e) {
            // Redis 장애 시 100ms 주기 풀 스택트레이스 폭탄 방지 — 다음 tick 이 곧 재시도하므로 1줄 WARN 로 충분
            log.warn("[queue] 입장 배치 실패 — 다음 tick 재시도 : {}", e.toString());
        }
    }
}
