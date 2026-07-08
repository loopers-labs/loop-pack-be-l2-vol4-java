package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 입장 처리(Admission) 도메인 서비스. 대기열 앞에서 N명을 꺼내(pollNext) 각자에게 입장 토큰을 발급한다.
 * WaitingQueue·EntryToken 은 한 admission 과정의 두 국면(동일 queue 도메인)이라 이 서비스가 조율한다. (결정 #6)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    /**
     * 대기열 앞에서 최대 batchSize 명을 입장시키고, 실제로 토큰을 발급한 인원 수를 반환한다.
     * 유저별 발급 실패는 격리한다(한 명 실패가 배치 전체를 막지 않게).
     */
    public int admit(int batchSize) {
        List<Long> admitted = waitingQueueRepository.pollNext(batchSize);
        int issued = 0;
        for (Long userId : admitted) {
            try {
                entryTokenRepository.issue(userId, TOKEN_TTL);
                issued++;
            } catch (Exception e) {
                // 이 유저는 이미 큐에서 빠졌으나 토큰 미발급 = 유실. 재시도 장치는 결정 #10(DEFERRED).
                // 지금은 유실을 관측 가능하게 남기고 다음 유저로 계속(격리) — 배치 전체를 막지 않는다.
                log.warn("입장 토큰 발급 실패 — 큐에서 제거됐으나 토큰 미발급(유실): userId={}", userId, e);
            }
        }
        return issued;
    }
}
