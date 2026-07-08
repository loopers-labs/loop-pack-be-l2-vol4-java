package com.loopers.application.order;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.EntryTokenValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 대기열 관문을 통과한 주문 유스케이스. 대기열은 주문 API 앞단의 관문이라, 기존 주문 use case({@link OrderFacade})를
 * 입장 검증(앞)과 토큰 소비(뒤)로 감싸는 데코레이터다. (결정 #4, Path A)
 * OrderFacade 는 입장과 무관한 주문 규칙 그대로 재사용하고, 관문 관심사만 여기서 조율한다.
 * {@code orderFacade.placeOrder} 를 별 빈으로 호출해 프록시를 경유하므로 그 {@code @Transactional} 이 정상 적용된다.
 */
@Component
@RequiredArgsConstructor
public class QueuedOrderFacade {

    private final EntryTokenValidator entryTokenValidator;
    private final OrderFacade orderFacade;
    private final EntryTokenRepository entryTokenRepository;

    public OrderInfo placeOrder(Long userId, OrderCommand.Place command) {
        entryTokenValidator.validate(userId);
        OrderInfo info = orderFacade.placeOrder(userId, command);
        entryTokenRepository.consume(userId);
        return info;
    }
}
