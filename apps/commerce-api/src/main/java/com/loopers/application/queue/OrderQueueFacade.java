package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.OrderQueueService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@RequiredArgsConstructor
@Component
public class OrderQueueFacade {

    private final OrderQueueService orderQueueService;
    private final EntryTokenRepository entryTokenRepository;

    // 예상 대기시간 추정용 처리량(초당 입장 인원). 순번 / 이 값 ≈ 남은 시간.
    @Value("${queue.throughput-per-second:175}")
    private long throughputPerSecond;

    public QueuePositionInfo enter(Long userId) {
        // 방금 진입했으므로 항상 대기 상태(토큰 없음).
        return failClosed(() -> waiting(orderQueueService.enter(userId)));
    }

    public QueuePositionInfo position(Long userId) {
        return failClosed(() -> describe(userId));
    }

    private QueuePositionInfo describe(Long userId) {
        long position = orderQueueService.position(userId);
        if (position > 0) {
            return waiting(position);
        }
        // 큐에 없음 → 스케줄러가 토큰을 발급했으면 "내 차례", 아니면 미진입/완료.
        String token = entryTokenRepository.find(userId).orElse(null);
        return new QueuePositionInfo(0, orderQueueService.totalWaiting(), 0, token);
    }

    private QueuePositionInfo waiting(long position) {
        long estimatedWaitSeconds = (long) Math.ceil((double) position / throughputPerSecond);
        return new QueuePositionInfo(position, orderQueueService.totalWaiting(), estimatedWaitSeconds, null);
    }

    // 대기열은 캐시(fail-open)와 정반대다. Redis 에러 시 우회 입장을 허용하면 DB 보호라는 본질이 무너지므로 503으로 막는다.
    private QueuePositionInfo failClosed(Supplier<QueuePositionInfo> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "대기열이 혼잡합니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
