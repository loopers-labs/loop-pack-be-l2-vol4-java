package com.loopers.application.order;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class OrderApplicationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderDomainService orderDomainService;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public OrderInfo createOrder(Long userId, OrderCommand command) {
        Map<Long, Integer> quantities = buildQuantities(command.items());
        Map<Long, ProductModel> products = loadProductsWithLock(quantities);
        long discountPrice = resolveDiscount(userId, command.couponId(), quantities, products);

        OrderModel order = orderDomainService.place(userId, quantities, products, discountPrice);
        products.values().forEach(productRepository::save);
        return OrderInfo.from(orderRepository.save(order));
    }

    private Map<Long, Integer> buildQuantities(List<OrderCommand.Item> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderCommand.Item item : items) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return quantities;
    }

    private Map<Long, ProductModel> loadProductsWithLock(Map<Long, Integer> quantities) {
        Map<Long, ProductModel> products = new HashMap<>();
        for (Long productId : quantities.keySet().stream().sorted().toList()) {
            ProductModel product = productRepository.findWithLock(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
            products.put(productId, product);
        }
        return products;
    }

    private long resolveDiscount(Long userId, Long couponId, Map<Long, Integer> quantities, Map<Long, ProductModel> products) {
        if (couponId == null) return 0L;

        UserCouponModel userCoupon = userCouponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + couponId + "] 쿠폰을 찾을 수 없습니다."));
        if (!userCoupon.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }
        CouponModel coupon = couponRepository.findById(userCoupon.getCouponId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다."));
        if (!userCoupon.isUsable(coupon.getExpiredAt())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 가능한 쿠폰이 아닙니다.");
        }
        long orderSubtotal = quantities.entrySet().stream()
            .mapToLong(e -> products.get(e.getKey()).getPrice() * e.getValue())
            .sum();
        long discountPrice = coupon.calculateDiscount(orderSubtotal);
        userCoupon.use();
        userCouponRepository.save(userCoupon);
        return discountPrice;
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, LocalDate startAt, LocalDate endAt) {
        List<OrderModel> orders;
        if (startAt != null && endAt != null) {
            ZonedDateTime start = startAt.atStartOfDay(ZONE);
            ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZONE);
            orders = orderRepository.findAllByUserIdAndCreatedAtBetween(userId, start, end);
        } else {
            orders = orderRepository.findAllByUserId(userId);
        }
        return orders.stream().map(OrderInfo::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long userId, Long orderId) {
        OrderModel order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderForAdmin(Long orderId) {
        OrderModel order = orderRepository.find(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
        return OrderInfo.from(order);
    }
}
