package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    /**
     * 대기열에 진입하고 현재 순번을 반환한다.
     * - 이미 대기 중인 유저의 재진입은 에러가 아니며, 기존 순번을 그대로 응답한다.
     * - 이미 입장 토큰을 발급받은 유저는 대기열에 다시 넣지 않고 READY(토큰 포함)를 응답한다.
     */
    public QueuePositionInfo enter(Long userId) {
        Optional<String> token = entryTokenRepository.find(userId);
        if (token.isPresent()) {
            return QueuePositionInfo.ready(token.get());
        }
        waitingQueueRepository.enter(userId, System.currentTimeMillis());
        return getPosition(userId);
    }

    /**
     * 현재 상태를 조회한다 (폴링 대상).
     * - 토큰이 발급된 유저 → READY + 토큰
     * - 대기 중인 유저 → WAITING + 순번/전체 대기 인원/예상 대기 시간/권장 폴링 주기
     * - 둘 다 아니면 → NOT_FOUND (미진입 — 토큰 TTL 만료 포함, 대기열 재진입 필요)
     */
    public QueuePositionInfo getPosition(Long userId) {
        Optional<String> token = entryTokenRepository.find(userId);
        if (token.isPresent()) {
            return QueuePositionInfo.ready(token.get());
        }
        long rank = waitingQueueRepository.findRank(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "대기열에 진입하지 않은 사용자입니다."));
        return QueuePositionInfo.waiting(rank, waitingQueueRepository.countWaiting());
    }
}
