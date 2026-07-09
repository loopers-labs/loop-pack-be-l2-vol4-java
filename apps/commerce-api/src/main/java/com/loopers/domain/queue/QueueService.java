package com.loopers.domain.queue;

import com.loopers.support.config.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class QueueService {  // 대기열 진입·순번·입장 처리를 담당하는 도메인 서비스.

    private final WaitingQueue waitingQueue;
    private final EntryTokenStore entryTokenStore;
    private final QueueProperties queueProperties;

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

    /**
     * 폴링용 상태 조회. 입장한 유저는 admitNext 의 ZPOPMIN 으로 대기열에서 이미 빠지고 토큰만 보유하므로,
     * 토큰 → 대기열 순번 → 미진입 순으로 판정한다.
     */
    public QueuePosition getStatus(String loginId) {
        Optional<String> token = entryTokenStore.find(loginId);
        if (token.isPresent()) {
            return QueuePosition.admitted(token.get());
        }
        long rank = waitingQueue.rank(loginId)
                                .orElseThrow(
                                        () -> new CoreException(ErrorType.NOT_FOUND, "[loginId = " + loginId + "] 대기열에 진입한 이력이 없습니다.")
                                );
        return QueuePosition.waiting(rank + 1, queueProperties.throughputPerSecond());
    }

    /** 대기열 앞에서 n 명을 꺼내 각자 입장 토큰을 발급한다(스케줄러가 주기적으로 호출). */
    public void admitNext(int n) {
        for (String loginId : waitingQueue.pollFirst(n)) {
            entryTokenStore.issue(loginId);
        }
    }

    /** 입장 토큰 검증. 미보유·불일치·만료 시 FORBIDDEN. */
    public void validateToken(String loginId, String token) {
        if (token == null || token.isBlank()) {
            throw new CoreException(ErrorType.FORBIDDEN, "입장 토큰이 필요합니다. 대기열을 통해 입장하세요.");
        }
        String issued = entryTokenStore.find(loginId)
                                        .orElseThrow(
                                                () -> new CoreException(ErrorType.FORBIDDEN, "[loginId = " + loginId + "] 입장 권한이 없거나 만료되었습니다.")
                                        );
        if (!issued.equals(token)) {
            throw new CoreException(ErrorType.FORBIDDEN, "입장 토큰이 유효하지 않습니다.");
        }
    }

    /** 주문 완료 후 토큰 소비(삭제). */
    public void consumeToken(String loginId) {
        entryTokenStore.delete(loginId);
    }
}
