package com.loopers.application.order;

import com.loopers.application.queue.WaitingQueueRepository;
import com.loopers.domain.order.OrderProductCommand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class QueuedOrderFacade {

    private final OrderFacade orderFacade;
    private final WaitingQueueRepository waitingQueueRepository;

    public OrderInfo createOrder(String userLoginId, List<OrderProductCommand> commands, Long couponId, String entryToken) {
        validateEntryToken(userLoginId, entryToken);
        OrderInfo orderInfo = orderFacade.createOrder(userLoginId, commands, couponId);
        waitingQueueRepository.deleteEntryToken(userLoginId);
        return orderInfo;
    }

    private void validateEntryToken(String userLoginId, String entryToken) {
        if (entryToken == null || entryToken.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "입장 토큰이 필요합니다.");
        }
        String savedToken = waitingQueueRepository.findEntryToken(userLoginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "유효한 입장 토큰이 없습니다."));
        if (!savedToken.equals(entryToken)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "입장 토큰이 올바르지 않습니다.");
        }
    }
}
