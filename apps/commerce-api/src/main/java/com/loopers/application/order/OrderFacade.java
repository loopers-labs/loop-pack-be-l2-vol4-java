package com.loopers.application.order;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderCreationService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final OrderCreationService orderCreationService;
    private final OrderRepository orderRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public OrderInfo placeOrder(OrderCriteria criteria) {
        // ① 응용 입력 → 도메인 서비스 입력
        List<OrderCreationService.OrderLine> lines = criteria.lines().stream()
                .map(l -> new OrderCreationService.OrderLine(l.productId(), l.quantity()))
                .toList();

        // ② 재고 차감 + 항목 스냅샷 + Aggregate 조립 (할인 전 총액까지)
        OrderModel order = orderCreationService.create(criteria.userId(), lines);

        // ③ 쿠폰이 있으면: 소유권 검증 → 할인 계산(최소금액 검증) → USED → 주문에 반영
        if (criteria.couponId() != null) {
            UserCouponModel userCoupon = userCouponRepository.findById(criteria.couponId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            "[id = " + criteria.couponId() + "] 쿠폰을 찾을 수 없습니다."));

            if (!userCoupon.getUserId().equals(criteria.userId())) {
                throw new CoreException(ErrorType.BAD_REQUEST, "본인 소유의 쿠폰이 아닙니다.");
            }

            Money discount = userCoupon.getDiscountPolicy().calculateDiscount(order.getTotalAmount());
            userCoupon.use(ZonedDateTime.now());          // 미사용·미만료 검증 + USED 전이
            order.applyCoupon(userCoupon.getId(), discount);
        }

        // ④ 저장 (cascade=ALL 이라 OrderItem 도 함께 INSERT)
        OrderModel saved = orderRepository.save(order);

        return OrderInfo.from(saved);
    }
}
