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
        claimEntryToken(userLoginId, entryToken);
        OrderInfo orderInfo;
        try {
            orderInfo = orderFacade.createOrder(userLoginId, commands, couponId);
        } catch (RuntimeException exception) {
            waitingQueueRepository.releaseEntryTokenClaim(userLoginId, entryToken);
            throw exception;
        }
        waitingQueueRepository.completeEntryToken(userLoginId, entryToken);
        return orderInfo;
    }

    private void claimEntryToken(String userLoginId, String entryToken) {
        if (entryToken == null || entryToken.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "입장 토큰이 필요합니다.");
        }
        if (!waitingQueueRepository.claimEntryToken(userLoginId, entryToken)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "입장 토큰이 유효하지 않거나 이미 사용 중입니다.");
        }
    }
}
