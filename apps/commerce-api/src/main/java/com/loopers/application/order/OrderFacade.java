package com.loopers.application.order;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

        // 쿠폰 조회는 상품 락 획득 "이전"에 — FOR UPDATE 보유 시간을 줄이고, 무효 쿠폰이면 락 없이 조기 실패
        UserCoupon userCoupon = (command.couponId() != null)
            ? userCouponRepository.find(command.couponId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."))
            : null;

        // 비관적 락 배치 조회 — productId 오름차순으로 잠가 다중 상품 주문 간 교차 데드락 방지
        List<Long> productIds = command.items().stream()
            .map(PlaceOrderCommand.Item::productId)
            .distinct()
            .sorted()
            .toList();
        Map<Long, Product> products = productRepository.findAllForUpdate(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderLine> lines = command.items().stream()
            .map(item -> {
                Product product = products.get(item.productId());
                if (product == null) {
                    throw new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + item.productId() + "] 상품을 찾을 수 없습니다.");
                }
                return new OrderLine(product, item.quantity());
            })
            .toList();

        Order order = orderService.place(user.getId(), lines, userCoupon, ZonedDateTime.now());

        lines.forEach(line -> productRepository.save(line.product())); // 재고 차감 반영
        if (userCoupon != null) {
            userCouponRepository.save(userCoupon); // USED 반영 — 커밋 시점 @Version 검증
        }
        Order saved = orderRepository.save(order);
        return OrderInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(String loginId, Long orderId) {
        User user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));
        Order order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(user.getId())) {
            // 타 유저 주문은 존재를 드러내지 않고 NOT_FOUND
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다.");
        }
        return OrderInfo.from(order);
    }
}
