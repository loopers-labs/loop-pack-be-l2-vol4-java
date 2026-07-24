package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;

    public WaitingQueueService(WaitingQueueRepository waitingQueueRepository) {
        this.waitingQueueRepository = waitingQueueRepository;
    }

    /**
     * 대기열에 진입시키고 현재 순번을 반환한다.
     * 이미 진입한 유저면 순번을 유지한다(멱등).
     */
    public QueuePosition enter(Long userId) {
        waitingQueueRepository.add(userId, System.currentTimeMillis());
        return getPosition(userId);
    }

    /**
     * 현재 순번과 전체 대기 인원을 조회한다.
     *
     * @throws CoreException 대기열에 없는 유저면 NOT_FOUND
     */
    public QueuePosition getPosition(Long userId) {
        long rank = waitingQueueRepository.rank(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "대기열에 없는 유저입니다."));
        long total = waitingQueueRepository.size();
        return new QueuePosition(rank, total);
    }

    /**
     * 대기열 앞에서 최대 {@code count}명을 꺼낸다(입장시킬 대상 선발).
     * 스케줄러가 주기적으로 호출한다.
     *
     * @return 꺼낸 userId 목록(진입 순서). 비어 있으면 빈 목록.
     */
    public List<Long> pollFront(long count) {
        return waitingQueueRepository.pollFront(count);
    }
}
