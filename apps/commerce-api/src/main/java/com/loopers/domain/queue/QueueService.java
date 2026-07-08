package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueService {  // 대기열 진입/순번/인원 조회를 담당하는 도메인 서비스.

    private final WaitingQueue waitingQueue;

    /** 대기열 진입 후 순번(1-based)을 반환한다. 재진입해도 최초 순번을 유지한다(ZADD NX). */
    public long enter(String loginId) {
        waitingQueue.enter(loginId);
        
        return getPosition(loginId);
    }

    /** 현재 순번(1-based). 대기열에 없으면 NOT_FOUND. */
    public long getPosition(String loginId) {
        return waitingQueue.rank(loginId)
                           .map(rank -> rank + 1)
                           .orElseThrow(
                                   () -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 대기열에 진입한 이력이 없습니다.")
                           );
    }

    /** 전체 대기 인원. */
    public long getWaitingCount() {
        return waitingQueue.size();
    }
}