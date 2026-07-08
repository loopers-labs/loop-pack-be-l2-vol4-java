package com.loopers.application.queue;

import com.loopers.domain.queue.OrderQueueService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.function.LongSupplier;

@RequiredArgsConstructor
@Component
public class OrderQueueFacade {

    private final OrderQueueService orderQueueService;

    public QueuePositionInfo enter(Long userId) {
        return failClosed(() -> orderQueueService.enter(userId));
    }

    public QueuePositionInfo position(Long userId) {
        return failClosed(() -> orderQueueService.position(userId));
    }

    // 대기열은 캐시(fail-open)와 정반대다. Redis 에러 시 우회 입장을 허용하면 DB 보호라는 본질이 무너지므로 503으로 막는다.
    private QueuePositionInfo failClosed(LongSupplier action) {
        try {
            return new QueuePositionInfo(action.getAsLong());
        } catch (DataAccessException e) {
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "대기열이 혼잡합니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
