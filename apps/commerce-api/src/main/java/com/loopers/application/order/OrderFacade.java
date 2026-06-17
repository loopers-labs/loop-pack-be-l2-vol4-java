package com.loopers.application.order;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;
    private final CouponService couponService;

    @Transactional
    public OrderInfo createOrder(String loginId, String loginPw, List<OrderRequest> requests, Long userCouponId) {
        if (requests == null || requests.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        UserModel user = userService.getUser(loginId, loginPw);

        // 다중 상품 주문 시 데드락 방지: 모든 주문이 productId 오름차순으로 재고 락을 잡도록
        // 차감 순서를 통일한다. (InnoDB 행 쓰기 락은 커밋까지 유지되므로 잠금 순서가 제각각이면 순환 대기 발생)
        List<OrderRequest> orderedRequests = requests.stream()
            .sorted(Comparator.comparing(OrderRequest::productId))
            .toList();

        List<OrderItemCommand> itemCommands = new ArrayList<>();
        long originalPrice = 0L;

        for (OrderRequest req : orderedRequests) {
            ProductModel product = productService.getProduct(req.productId());
            productService.deductStock(req.productId(), req.quantity());
            itemCommands.add(new OrderItemCommand(
                product.getId(),
                product.getName(),
                product.getPrice(),
                req.quantity()
            ));
            originalPrice += product.getPrice() * req.quantity();
        }

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

        return OrderInfo.of(order, savedItems.stream().map(OrderItemInfo::from).toList());
    }

    public record OrderRequest(Long productId, int quantity) {}
}
