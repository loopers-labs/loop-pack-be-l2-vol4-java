package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 주문 대기열 유스케이스. 상태는 전부 Redis(seam 뒤) — DB 를 건드리지 않아
 * 트래픽 폭증 시에도 하류(DB/PG)에 부하를 전달하지 않는다(Back-pressure).
 * 유저 식별자는 loginId(String) — userId(Long PK)와 혼동 금지.
 */
@Component
public class QueueFacade {

    private static final Logger log = LoggerFactory.getLogger(QueueFacade.class);

    // polling 주기 정책(2차-1) — 순번이 뒤일수록 폴링을 늦춰 대기 인원 수에 비례하는 조회 부하를 줄인다.
    // 구간 경계는 운영 튜닝 대상이 아니라 UX 정책이므로 프로퍼티가 아닌 상수로 응집한다.
    private static final long FAST_POLL_MAX_POSITION = 100;
    private static final long MEDIUM_POLL_MAX_POSITION = 1_000;
    private static final long FAST_POLL_INTERVAL_SECONDS = 1;
    private static final long MEDIUM_POLL_INTERVAL_SECONDS = 3;
    private static final long SLOW_POLL_INTERVAL_SECONDS = 5;

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final boolean orderGateEnabled;
    private final long admissionIntervalMs;
    private final int admissionBatchSize;
    private final long tokenTtlSeconds;

    public QueueFacade(
        WaitingQueueRepository waitingQueueRepository,
        EntryTokenRepository entryTokenRepository,
        QueueProperties properties
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.entryTokenRepository = entryTokenRepository;
        this.orderGateEnabled = properties.orderGate().enabled();
        this.admissionIntervalMs = properties.admission().intervalMs();
        this.admissionBatchSize = properties.admission().batchSize();
        this.tokenTtlSeconds = properties.token().ttlSeconds();
    }

    /**
     * 대기열 진입(멱등). 이미 대기 중이면 기존 순번을, 이미 토큰 보유면 position 0 + 토큰을 반환한다.
     * 유저 존재 여부를 DB 로 검증하지 않는다 — 대기열은 하류 보호가 목적이라 여기서 DB 를 치지 않는다.
     */
    public QueueInfo enter(String loginId) {
        requireQueueOpen();
        Optional<String> issuedToken = entryTokenRepository.find(loginId);
        if (issuedToken.isPresent()) {
            return admittedInfo(issuedToken.get());
        }
        waitingQueueRepository.enqueue(loginId, System.currentTimeMillis()); // ZADD NX — 재진입 시 score 유지
        // enqueue 직후 rank 미스 = 그 사이 스케줄러가 입장 처리한 희귀 레이스. admitNextBatch 는
        // "토큰 발급 → 큐 제거" 순서라 이 시점엔 토큰이 존재하므로, 현재 상태 재조회로 admitted 응답이 된다.
        return waitingInfo(loginId).orElseGet(() -> getPosition(loginId));
    }

    /** 현재 순번 조회(polling). 토큰 보유 → position 0 + 토큰 / 대기 중 → 순번 / 둘 다 아님 → 404. */
    public QueueInfo getPosition(String loginId) {
        requireQueueOpen();
        Optional<String> issuedToken = entryTokenRepository.find(loginId);
        if (issuedToken.isPresent()) {
            return admittedInfo(issuedToken.get());
        }
        return waitingInfo(loginId).orElseThrow(() ->
            new CoreException(ErrorType.NOT_FOUND, "대기열에 진입해 있지 않습니다."));
    }

    /**
     * 게이트 off(평시)면 대기열 API 자체를 거부한다 — 스케줄러가 게이트 스위치와 수명을 같이 하므로,
     * off 상태에서 진입을 받으면 아무도 입장시키지 않아 영원히 대기하는 함정이 된다.
     * 행사 스위치 하나(queue.order-gate.enabled)가 게이트·스케줄러·대기열 API 세 개의 수명을 함께 결정한다.
     */
    private void requireQueueOpen() {
        if (!orderGateEnabled) {
            throw new CoreException(ErrorType.BAD_REQUEST, "대기열 미운영 중입니다. 바로 주문할 수 있습니다.");
        }
    }

    /**
     * 맨 앞 배치에 입장 토큰을 발급하고 대기열에서 제거한다 — 스케줄러가 interval 마다 호출.
     *
     * 순서가 핵심: "토큰 발급(SET) 먼저 → 큐 제거(ZREM) 나중".
     * 반대로 하면(ZPOPMIN 먼저) 제거~발급 사이에 "큐에도 토큰에도 없는" 윈도가 생겨, polling 이 404 를
     * 받고 재진입해 ghost 엔트리(토큰+대기 이중 등록)가 된다. 지금 순서의 윈도는 "토큰+큐 동시 보유"인데
     * enter/getPosition 모두 토큰을 먼저 확인하므로 admitted 로 응답되어 무해하다.
     * 발급 실패 유저는 제거하지 않고 큐에 남긴다 — 다음 tick 이 재시도한다(유실 대신 지연).
     * 단일 인스턴스 전제(기존 스케줄러 3종과 동일) — 다중 인스턴스면 같은 배치를 중복 peek 해 중복 발급된다.
     */
    public void admitNextBatch() {
        List<String> nextLoginIds = waitingQueueRepository.peekNextBatch(admissionBatchSize);
        if (nextLoginIds.isEmpty()) {
            return;
        }
        List<String> issuedLoginIds = new ArrayList<>();
        for (String loginId : nextLoginIds) {
            try {
                entryTokenRepository.issue(loginId, UUID.randomUUID().toString(), Duration.ofSeconds(tokenTtlSeconds));
                issuedLoginIds.add(loginId);
            } catch (Exception e) {
                // 한 명의 발급 실패가 배치 전체를 막지 않게 격리 — 실패 유저는 큐에 남아 다음 tick 에 재시도
                log.warn("[queue] 입장 토큰 발급 실패 — 큐에 남겨 다음 tick 에 재시도 loginId={} : {}", loginId, e.toString());
            }
        }
        if (!issuedLoginIds.isEmpty()) {
            waitingQueueRepository.remove(issuedLoginIds);
        }
    }

    private QueueInfo admittedInfo(String token) {
        return QueueInfo.admitted(token, waitingQueueRepository.countWaiting());
    }

    private Optional<QueueInfo> waitingInfo(String loginId) {
        return waitingQueueRepository.findRank(loginId).map(rank -> {
            long position = rank + 1;
            return QueueInfo.waiting(
                position,
                waitingQueueRepository.countWaiting(),
                estimatedWaitSeconds(position),
                suggestedPollIntervalSeconds(position)
            );
        });
    }

    /** 예상 대기 시간(초) = ceil(position / 초당 배출량), 초당 배출량 = batchSize × (1000 / intervalMs). */
    private long estimatedWaitSeconds(long position) {
        // ceil(position / (batchSize × 1000 / intervalMs)) 를 정수 연산으로: ceil(position × intervalMs / (batchSize × 1000))
        return Math.ceilDiv(position * admissionIntervalMs, admissionBatchSize * 1_000L);
    }

    private long suggestedPollIntervalSeconds(long position) {
        if (position <= FAST_POLL_MAX_POSITION) {
            return FAST_POLL_INTERVAL_SECONDS;
        }
        if (position <= MEDIUM_POLL_MAX_POSITION) {
            return MEDIUM_POLL_INTERVAL_SECONDS;
        }
        return SLOW_POLL_INTERVAL_SECONDS;
    }
}
