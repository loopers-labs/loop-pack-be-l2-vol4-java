package com.loopers.queue.application;

import com.loopers.queue.domain.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 데모/개발용. 대기열 앞에 더미 대기자를 채워 "순번이 줄어드는" 모습을 눈으로 볼 수 있게 한다.
 * 더미는 낮은 score(과거)로 넣어 실제 유저(score=현재시각)보다 항상 앞에 선다.
 */
@Service
@RequiredArgsConstructor
public class QueueDevService {

    private final WaitingQueueRepository waitingQueueRepository;

    /** 더미 대기자 count 명을 앞에 세운다. 현재 전체 대기 인원을 돌려준다. */
    public long fill(int count) {
        for (int i = 1; i <= count; i++) {
            waitingQueueRepository.add("dummy-" + i, i);
        }
        return waitingQueueRepository.size();
    }
}
