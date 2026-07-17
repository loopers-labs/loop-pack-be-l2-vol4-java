package com.loopers.interfaces.scheduling;

import com.loopers.config.waitingqueue.WaitingQueueProperties;
import com.loopers.domain.waitingqueue.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 입장 토큰 발급 배치(FR-3). 매 주기 활성 여유분만큼 대기열 앞에서 pop해 토큰을 발급한다.
 *
 * <p>발급 배치(count→pop→issue)는 {@link com.loopers.domain.waitingqueue.TokenIssuer}의 단일 Lua 스크립트로
 * 원자 실행되므로, 다중 인스턴스가 동시에 발화해도 활성 상한 초과 발급이 없다(분산 락 불필요, NFR-5).
 * 여러 인스턴스가 겹치면 뒤 실행은 여유분 0으로 빈 배치를 반환할 뿐이다. 관문 비활성(평시)이면 no-op.
 *
 * <p>주기는 {@code waiting-queue.scheduler-interval-seconds}(초) × 1000ms. 값 근거: docs/week8 §D2.
 */
@Slf4j
@Profile("!test")
@RequiredArgsConstructor
@Component
public class EntryTokenScheduler {

    private final WaitingQueueService waitingQueueService;
    private final WaitingQueueProperties properties;

    @Scheduled(
        fixedDelayString = "${waiting-queue.scheduler-interval-seconds:2}000",
        initialDelayString = "5000")
    public void issueTokens() {
        if (!properties.enabled()) {
            return;
        }
        int issued = waitingQueueService.issueBatch();
        if (issued > 0) {
            log.info("입장 토큰 발급 배치: {}명 입장", issued);
        }
    }
}
