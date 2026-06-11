package com.loopers.application.order;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
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

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public OrderInfo createOrder(String loginId, PlaceOrderCommand command) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        List<OrderLine> lines = command.items().stream()
            .map(item -> {
                ProductModel product = productRepository.find(item.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + item.productId() + "] 상품을 찾을 수 없습니다."));
                return new OrderLine(product, item.quantity());
            })
            .toList();

        UserCoupon userCoupon = (command.couponId() != null)
            ? userCouponRepository.find(command.couponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."))
            : null;

        OrderModel order = orderService.place(user.getId(), lines, userCoupon, ZonedDateTime.now());

        lines.forEach(line -> productRepository.save(line.product())); // 재고 차감 반영
        if (userCoupon != null) {
            userCouponRepository.save(userCoupon); // USED 반영 — 커밋 시점 @Version 검증
        }
        OrderModel saved = orderRepository.save(order);
        return OrderInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(String loginId, Long orderId) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
        OrderModel order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(user.getId())) {
            // 타 유저 주문은 존재를 드러내지 않고 NOT_FOUND
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
    }
}
