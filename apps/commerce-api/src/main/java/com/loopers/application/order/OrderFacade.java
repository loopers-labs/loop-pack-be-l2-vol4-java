package com.loopers.application.order;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.order.OrderCompletedEvent;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockDeductionCommand;
import com.loopers.domain.product.StockDeductionResult;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final CouponService couponService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderInfo createOrder(String loginId, String loginPw, List<OrderRequest> requests, Long userCouponId) {
        if (requests == null || requests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        UserModel user = userService.getUser(loginId, loginPw);

        // 재고 차감 순서 통일(데드락 방지) 등 잠금 전략은 도메인(ProductService)이 책임진다.
        List<StockDeductionCommand> deductionCommands = requests.stream()
            .map(req -> new StockDeductionCommand(req.productId(), req.quantity()))
            .toList();
        List<StockDeductionResult> deducted = productService.deductStocks(deductionCommands);

        List<OrderItemCommand> itemCommands = deducted.stream()
            .map(d -> new OrderItemCommand(d.productId(), d.productName(), d.price(), d.quantity()))
            .toList();
        long originalPrice = deducted.stream()
            .mapToLong(d -> d.price() * d.quantity())
            .sum();

        long discountAmount = 0L;
        long finalPrice = originalPrice;

        if (userCouponId != null) {
            UserCouponModel userCoupon = couponService.useCoupon(user.getId(), userCouponId);
            CouponModel coupon = couponService.getCoupon(userCoupon.getCouponId());
            discountAmount = coupon.calculateDiscount(originalPrice);
            finalPrice = Math.max(0L, originalPrice - discountAmount);
        }

        OrderModel order = orderService.createOrder(
            user.getId(), originalPrice, discountAmount, finalPrice, userCouponId, itemCommands
        );
        List<OrderItemModel> savedItems = orderService.getOrderItems(order.getId());

        // 주문 완료 사실을 발행한다. 알림/유저행동 로깅/판매량 집계 같은 부가 관심사는 이 트랜잭션 커밋 뒤
        // AFTER_COMMIT 리스너가 각자 구독해 처리한다(본 주문 로직과 경계 분리).
        List<OrderCompletedEvent.Line> eventLines = itemCommands.stream()
            .map(c -> new OrderCompletedEvent.Line(c.productId(), c.quantity()))
            .toList();
        eventPublisher.publishEvent(new OrderCompletedEvent(order.getId(), user.getId(), finalPrice, eventLines));

        return OrderInfo.of(order, savedItems.stream().map(OrderItemInfo::from).toList());
    }

    public record OrderRequest(Long productId, int quantity) {}
}
