package com.loopers.application.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class WaitingQueueFacade {

    private final WaitingQueueRepository waitingQueueRepository;
    private final WaitingQueueProperties properties;

    public WaitingQueueInfo.Position enter(String userLoginId) {
        validateUserLoginId(userLoginId);
        Optional<String> entryToken = waitingQueueRepository.findEntryToken(userLoginId);
        if (entryToken.isPresent()) {
            return WaitingQueueInfo.Position.admitted(waitingQueueRepository.countWaitingUsers(), entryToken.get());
        }

        waitingQueueRepository.enqueueIfAbsent(userLoginId);
        return position(userLoginId);
    }

    public WaitingQueueInfo.Position position(String userLoginId) {
        validateUserLoginId(userLoginId);
        Optional<String> entryToken = waitingQueueRepository.findEntryToken(userLoginId);
        long waitingCount = waitingQueueRepository.countWaitingUsers();
        if (entryToken.isPresent()) {
            return WaitingQueueInfo.Position.admitted(waitingCount, entryToken.get());
        }

        Long zeroBasedRank = waitingQueueRepository.findRank(userLoginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "대기열에 진입하지 않은 사용자입니다."));
        long position = zeroBasedRank + 1;
        return WaitingQueueInfo.Position.waiting(position, waitingCount, estimatedWaitSeconds(position));
    }

    private long estimatedWaitSeconds(long position) {
        int admitPerSecond = properties.admitPerSecond();
        return Math.max(1L, (long) Math.ceil((double) position / admitPerSecond));
    }

    private void validateUserLoginId(String userLoginId) {
        if (userLoginId == null || userLoginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 로그인 ID는 비어있을 수 없습니다.");
        }
    }
}
