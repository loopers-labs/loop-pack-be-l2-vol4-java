package com.loopers.queue.application;

import com.loopers.queue.domain.AdmissionPolicy;
import com.loopers.queue.domain.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@RequiredArgsConstructor
@Service
public class QueueService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final OrderQueueAdmissionProperties admissionProperties;

    /** 대기열에 진입시키고 순번을 돌려준다. 이미 있으면 순번을 유지한다. */
    public QueueResult.Enter enter(String userId) {
        waitingQueueRepository.add(userId, System.currentTimeMillis());
        return new QueueResult.Enter(waitingQueueRepository.rank(userId));
    }

    /** 현재 순번과 예상 대기시간, 다음 폴링 간격을 돌려준다. 대기열에 없으면 예외. */
    public QueueResult.Position position(String userId) {
        Long rank = waitingQueueRepository.rank(userId);
        if (rank == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "대기열에 진입하지 않았습니다.");
        }
        Duration wait = AdmissionPolicy.estimatedWait(rank, admissionProperties.tps());
        Duration poll = AdmissionPolicy.pollInterval(rank);
        return new QueueResult.Position(rank, wait.getSeconds(), poll.toMillis());
    }
}
