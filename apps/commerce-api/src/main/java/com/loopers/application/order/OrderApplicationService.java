package com.loopers.application.order;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderCommand;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderApplicationService {

    private final OrderDomainService orderDomainService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public OrderInfo.Created place(OrderCriteria.Place command) {
        List<OrderCommand.OrderLine> lines = command.lines()
                .stream()
                .map(OrderCriteria.Line::toDomain)
                .toList();

        Set<Long> productIds = lines.stream()
                .map(OrderCommand.OrderLine::productId)
                .collect(Collectors.toSet());
        ZonedDateTime now = ZonedDateTime.now();

        // 상품은 스냅샷(이름·단가)용이라 비-락 조회.
        List<Product> products = productRepository.findAllByIds(productIds);

        // 쿠폰 검증을 재고 비관락 '전에' 끝내, 타인/사용된 쿠폰 같은 흔한 실패가 핫 로우 락을 점유하지 않게 한다(fail-cheap-first).
        UserCoupon userCoupon = command.couponId() == null ? null
                : userCouponRepository.find(command.couponId())
                        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        if (userCoupon != null) {
            userCoupon.assertUsableBy(command.userId(), now);
        }

        // 재고 차감 대상인 inventory 만 비관락으로 잠근다(product_id 오름차순).
        List<Inventory> inventories = inventoryRepository.findAllByProductIdsForUpdate(productIds);

        Order order = orderDomainService.create(command.userId(), products, inventories, lines, userCoupon, now);
        Order saved = orderRepository.save(order);

        if (userCoupon != null) {
            userCoupon.use(saved.getId(), now);
        }
        return OrderInfo.Created.from(saved);
    }

    @Transactional(readOnly = true)
    public OrderInfo.Detail getMyOrder(Long userId, Long orderId) {
        Order order = orderRepository.find(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인 주문만 조회할 수 있습니다.");
        }
        return OrderInfo.Detail.from(order);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderInfo.ListItem> getMyOrders(OrderCriteria.MySearch command) {
        return orderRepository.findAllByUser(command.toDomain()).map(OrderInfo.ListItem::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo.Detail getOrder(Long orderId) {
        Order order = orderRepository.find(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        return OrderInfo.Detail.from(order);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderInfo.ListItem> getAllOrders(OrderCriteria.AdminSearch command) {
        return orderRepository.findAll(command.toDomain()).map(OrderInfo.ListItem::from);
    }
}
