package com.loopers.application.waitingqueue;

import com.loopers.config.waitingqueue.WaitingQueueProperties;
import com.loopers.domain.waitingqueue.QueueSnapshot;
import com.loopers.domain.waitingqueue.QueueStatus;
import com.loopers.domain.waitingqueue.ThroughputPolicy;
import com.loopers.domain.waitingqueue.WaitingQueueService;
import com.loopers.domain.waitingqueue.WaitingQueueStatusView;
import com.loopers.infrastructure.waitingqueue.RankCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 대기열 대고객 유스케이스 조립.
 *
 * <p>순번 조회는 RankCache로 짧게 캐싱해 대량 폴링을 흡수한다(D5·NFR-7). 응답에 권장 폴링 주기
 * (pollAfterSeconds)를 실어 대기 규모에 따라 클라이언트 폴링 간격을 서버가 제어한다.
 *
 * <p>주문 API 진입 가드(validateEntry/releaseEntry)는 {@code waiting-queue.enabled} 플래그로 토글한다.
 * 평시(false)엔 no-op이라 기존 주문 흐름을 바꾸지 않고, 블랙프라이데이(true)에만 토큰을 요구한다.
 */
@RequiredArgsConstructor
@Component
public class WaitingQueueFacade {

    private final WaitingQueueService service;
    private final ThroughputPolicy policy;
    private final RankCache rankCache;
    private final WaitingQueueProperties properties;

    public RankView enter(Long userId) {
        QueueSnapshot snapshot = service.enter(userId);
        RankView view = RankView.from(snapshot, pollAfter(snapshot));
        rankCache.put(userId, view); // 진입 직후 폴링이 최신 상태를 보도록 즉시 갱신
        return view;
    }

    public RankView getRank(Long userId) {
        return rankCache.get(userId).orElseGet(() -> {
            QueueSnapshot snapshot = service.resolve(userId);
            RankView view = RankView.from(snapshot, pollAfter(snapshot));
            rankCache.put(userId, view);
            return view;
        });
    }

    public WaitingQueueStatusView status() {
        return service.status();
    }

    /** 주문 진입 시 토큰 검증(FR-5). 관문 비활성이면 통과. 유효하지 않으면 CoreException(FORBIDDEN). */
    public void validateEntry(Long userId, String token) {
        if (!properties.enabled()) {
            return;
        }
        service.validateToken(userId, token);
    }

    /** 주문 성공 확정 시 토큰 소모(D3). 관문 비활성이면 no-op. */
    public void releaseEntry(Long userId, String token) {
        if (!properties.enabled() || token == null || token.isBlank()) {
            return;
        }
        service.consume(userId, token);
    }

    private int pollAfter(QueueSnapshot snapshot) {
        if (snapshot.status() != QueueStatus.WAITING) {
            return 0; // READY/NOT_IN_QUEUE → 폴링 중단 신호
        }
        return policy.pollAfterSeconds(snapshot.aheadCount());
    }
}
