package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WaitingQueueFacade {

    private final WaitingQueueRepository waitingQueueRepository;

    /** 대기열 진입: 진입시키고 현재 순번과 전체 대기 인원을 돌려준다. 이미 대기 중이면 순번을 유지한다. */
    public QueueInfo enter(Long userId) {
        QueuePosition position = waitingQueueRepository.enroll(userId);
        return QueueInfo.of(position, waitingQueueRepository.size());
    }

    /** 순번 조회: 대기 중인 유저의 현재 순번과 전체 대기 인원을 돌려준다. */
    public QueueInfo getPosition(Long userId) {
        QueuePosition position = waitingQueueRepository.positionOf(userId)
            .orElseThrow(() -> new CoreException(ErrorType.QUEUE_ENTRY_NOT_FOUND));
        long totalWaiting = waitingQueueRepository.size();
        return QueueInfo.of(position, totalWaiting);
    }
}
