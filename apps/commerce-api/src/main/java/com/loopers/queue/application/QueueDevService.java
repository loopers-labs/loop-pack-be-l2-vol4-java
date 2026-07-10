package com.loopers.queue.application;

import com.loopers.queue.domain.EntryTokenStore;
import com.loopers.queue.domain.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 데모/개발용. 대기열 앞에 더미 대기자를 채워 "순번이 줄어드는" 모습을 보여주고,
 * 부하테스트에서 스케줄러를 거치지 않고 입장 토큰을 즉시 발급한다(order API 순수 측정용).
 */
@Service
@RequiredArgsConstructor
public class QueueDevService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenStore entryTokenStore;
    private final OrderQueueEntryTokenProperties entryTokenProperties;

    /** 더미 대기자 count 명을 앞에 세운다. 현재 전체 대기 인원을 돌려준다. */
    public long fill(int count) {
        for (int i = 1; i <= count; i++) {
            waitingQueueRepository.add("dummy-" + i, i);
        }
        return waitingQueueRepository.size();
    }

    /** 스케줄러를 거치지 않고 해당 유저에게 입장 토큰을 즉시 발급한다(부하테스트 전용). */
    public String issueToken(String userId) {
        return entryTokenStore.issue(userId, entryTokenProperties.ttl());
    }
}
