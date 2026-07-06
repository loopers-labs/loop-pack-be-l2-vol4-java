package com.loopers.queue.application;

import com.loopers.queue.domain.AdmissionPolicy;
import com.loopers.queue.domain.EntryTokenStore;
import com.loopers.queue.domain.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class QueueService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenStore entryTokenStore;
    private final OrderQueueAdmissionProperties admissionProperties;
    private final OrderQueueEntryTokenProperties entryTokenProperties;

    /** 대기열에 진입시키고 순번을 돌려준다. 이미 있으면 순번을 유지한다. */
    public QueueResult.Enter enter(String userId) {
        waitingQueueRepository.add(userId, System.currentTimeMillis());
        return new QueueResult.Enter(waitingQueueRepository.rank(userId));
    }

    /** 현재 순번과 예상 대기시간, 다음 폴링 간격을 돌려준다. 입장했으면 토큰을, 대기열에 없으면 예외. */
    public QueueResult.Position position(String userId) {
        Optional<String> token = entryTokenStore.find(userId);
        if (token.isPresent()) {
            return new QueueResult.Position(0L, 0L, 0L, token.get());
        }
        Long rank = waitingQueueRepository.rank(userId);
        if (rank == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "대기열에 진입하지 않았습니다.");
        }
        Duration wait = AdmissionPolicy.estimatedWait(rank, admissionProperties.tps());
        Duration poll = AdmissionPolicy.pollInterval(rank);
        return new QueueResult.Position(rank, wait.getSeconds(), poll.toMillis(), null);
    }

    /** 대기열 앞에서 batch 만큼 꺼내 입장 토큰을 발급한다. 발급 인원 수를 돌려준다. */
    public int admit() {
        List<String> admitted = waitingQueueRepository.popFront(admissionProperties.batchSize());
        admitted.forEach(userId -> entryTokenStore.issue(userId, entryTokenProperties.ttl()));
        return admitted.size();
    }
}
