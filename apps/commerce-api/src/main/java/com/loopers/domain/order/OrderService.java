package com.loopers.domain.order;

import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final UserCouponService userCouponService;

    @Transactional
    public OrderModel createPendingOrder(Long userId, List<OrderLine> lines, Long couponId) {
        List<OrderItemModel> items = new ArrayList<>();
        for (OrderLine line : lines) {
            ProductModel product = productService.getProduct(line.productId());
            productStockService.decreaseStock(line.productId(), line.quantity());
            items.add(OrderItemModel.of(
                    line.productId(),
                    product.getName().value(),
                    product.getPrice().value(),
                    line.quantity()
            ));
        }

        long originalAmount = items.stream().mapToLong(OrderItemModel::subtotal).sum();
        long discountAmount = couponId == null ? 0L : userCouponService.use(userId, couponId, originalAmount);

        OrderModel order = OrderModel.of(userId, items, couponId, discountAmount);
        return orderRepository.save(order);
    }

    @Transactional
    public OrderModel confirm(Long orderId) {
        OrderModel order = getOrder(orderId);
        order.confirm();
        return order;
    }

    @Transactional
    public OrderModel fail(Long orderId) {
        OrderModel order = getOrder(orderId);
        order.fail();
        for (OrderItemModel item : order.getOrderItems()) {
            productStockService.increaseStock(item.getProductId(), item.getQuantity());
        }
        if (order.getCouponId() != null) {
            userCouponService.restore(order.getUserId(), order.getCouponId());
        }
        return order;
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long orderId) {
        return orderRepository.find(orderId)
                .orElseThrow(
                    () -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")
                );
    }
}