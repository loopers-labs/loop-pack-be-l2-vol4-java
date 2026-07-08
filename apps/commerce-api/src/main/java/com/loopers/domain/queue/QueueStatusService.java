package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 순번 조회 유스케이스가 쓰는 상태 판별 도메인 서비스. queue 도메인의 두 port(대기열·입장토큰)를 엮어
 * 유저가 Waiting/Admitted/없음 중 어느 상태인지 결정한다. (이 "결정"이 서비스의 책임 → Facade 로 새지 않게 함)
 * 토큰은 입장 처리(큐에서 제거) 후에만 존재하므로 <b>토큰을 먼저</b> 확인한다 = 있으면 이미 입장됨.
 */
@Component
@RequiredArgsConstructor
public class QueueStatusService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    public QueueStatus statusOf(Long userId) {
        return entryTokenRepository.find(userId)
            .<QueueStatus>map(QueueStatus.Admitted::new)
            .orElseGet(() -> waitingQueueRepository.positionOf(userId)
                .<QueueStatus>map(position -> new QueueStatus.Waiting(position, waitingQueueRepository.size()))
                .orElseThrow(() -> new CoreException(ErrorType.QUEUE_ENTRY_NOT_FOUND)));
    }
}
