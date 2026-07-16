package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.QueuePolicy;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 대기열 진입·순번 조회 유스케이스. DB 를 타지 않으므로 트랜잭션이 없다.
 *
 * <p>진입은 멱등이다 — 이미 대기 중이면 ZADD NX 가 흡수해 기존 순번을 유지하고,
 * 이미 토큰을 받은 유저면 대기열에 다시 세우지 않고 토큰을 돌려준다
 * (재진입시키면 토큰 TTL 만 태우며 줄을 두 번 서게 된다).</p>
 */
@RequiredArgsConstructor
@Component
public class QueueApplicationService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;
    private final QueuePolicy queuePolicy;

    public QueueInfo.Position enter(Long userId) {
        Optional<String> issued = entryTokenRepository.find(userId);
        if (issued.isPresent()) {
            return QueueInfo.Position.ready(issued.get(), waitingQueueRepository.size());
        }
        waitingQueueRepository.enter(userId, System.currentTimeMillis());
        return resolve(userId);
    }

    public QueueInfo.Position getPosition(Long userId) {
        return resolve(userId);
    }

    /**
     * 토큰 → 대기열 순으로 상태를 판정한다. 둘 다 없으면 토큰을 한 번 더 확인하는데,
     * 스케줄러의 pop → 토큰 발급 사이 찰나에 조회가 끼어든 경우를 흡수하기 위함이다.
     * 그래도 없으면 미진입(또는 토큰 만료 후 이탈) — 재진입을 안내한다.
     */
    private QueueInfo.Position resolve(Long userId) {
        long totalWaiting = waitingQueueRepository.size();
        Optional<String> token = entryTokenRepository.find(userId);
        if (token.isPresent()) {
            return QueueInfo.Position.ready(token.get(), totalWaiting);
        }
        Optional<Long> rank = waitingQueueRepository.rank(userId);
        if (rank.isPresent()) {
            long position = rank.get() + 1; // 0-based rank → 1-based 순번
            return QueueInfo.Position.waiting(
                    position,
                    queuePolicy.estimatedWaitSeconds(position),
                    queuePolicy.pollAfterMillis(position),
                    totalWaiting
            );
        }
        return entryTokenRepository.find(userId)
                .map(token2 -> QueueInfo.Position.ready(token2, totalWaiting))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "대기열에 없습니다. 대기열에 진입해 주세요."));
    }
}
