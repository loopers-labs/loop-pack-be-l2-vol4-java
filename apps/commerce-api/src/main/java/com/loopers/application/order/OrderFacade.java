package com.loopers.application.order;

import com.loopers.domain.queue.EntryTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 흐름을 조율하는 오케스트레이터. 트랜잭션을 직접 걸지 않는다.
 * 원자적 주문생성(재고+쿠폰+주문 저장)은 OrderRegistrationService 의 트랜잭션에 위임한다.
 * 결제는 주문 생성과 분리되어 별도 API(PaymentFacade.pay)로 트리거된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderFacade {
    private final OrderRegistrationService orderRegistrationService;
    private final EntryTokenRepository entryTokenRepository;

    public OrderInfo place(Long userId, List<OrderLineCommand> commands, Long couponId) {
        OrderInfo orderInfo = OrderInfo.from(orderRegistrationService.register(userId, commands, couponId));
        // 주문 성공 후 입장 토큰을 소진한다(1회용). 주문 실패 시엔 여기 도달하지 않아 토큰이 유지되어 TTL 내 재시도가 허용된다.
        // 삭제 실패는 주문 성공에 영향 주지 않도록 흡수한다 — 남은 토큰은 TTL 로 회수된다.
        try {
            entryTokenRepository.delete(userId);
        } catch (Exception e) {
            log.warn("입장 토큰 삭제 실패 — TTL 로 회수됩니다. userId={}", userId, e);
        }
        return orderInfo;
    }
}
