package com.loopers.tddstudy.application.order;

import com.loopers.tddstudy.domain.coupon.Coupon;
import com.loopers.tddstudy.domain.coupon.CouponRepository;
import com.loopers.tddstudy.domain.coupon.UserCoupon;
import com.loopers.tddstudy.domain.coupon.UserCouponRepository;
import com.loopers.tddstudy.domain.order.Order;
import com.loopers.tddstudy.domain.order.OrderItem;
import com.loopers.tddstudy.domain.order.OrderRepository;
import com.loopers.tddstudy.domain.order.PaymentGateway;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentGateway paymentGateway;

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        PaymentGateway paymentGateway,
                        CouponRepository couponRepository,
                        UserCouponRepository userCouponRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.paymentGateway = paymentGateway;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
    }

    public Order createOrder(Long userId, List<OrderItemRequest> items,Long couponId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("주문 항목이 비어있습니다.");
        }
        // 1. 상품 조회 및 유효성 확인
        Map<Long, Product> productMap = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            Product product = productRepository.findByIdWithLock(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
            if (!product.isActive()) {
                throw new IllegalArgumentException("상품을 찾을 수 없습니다.");
            }
            productMap.put(item.productId(), product);
        }
        // 상품 조회 후, 쿠폰 처리 전
        int originalAmount = items.stream()
                .mapToInt(item -> productMap.get(item.productId()).getPrice() * item.quantity())
                .sum();

        int discountAmount = 0;
        if (couponId != null) {
            // UserCoupon 락 걸고 조회
            UserCoupon userCoupon = userCouponRepository.findByIdWithLock(couponId)
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다."));

            // 본인 소유 확인
            if (!userCoupon.getUserId().equals(userId)) {
                throw new IllegalArgumentException("본인 소유의 쿠폰이 아닙니다.");
            }

            // 사용 가능 여부 확인
            if (!userCoupon.isAvailable()) {
                throw new IllegalArgumentException("사용 불가능한 쿠폰입니다.");
            }

            // Coupon 템플릿 조회해서 할인 금액 계산
            Coupon coupon = couponRepository.findById(userCoupon.getCouponId())
                    .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다."));

            // 만료 확인
            if (coupon.isExpired()) {
                throw new IllegalArgumentException("만료된 쿠폰입니다.");
            }

            discountAmount = coupon.discount(originalAmount);

            // 쿠폰 사용 처리
            userCoupon.use();
            userCouponRepository.save(userCoupon);
        }


        // 2. 재고 원자적 확인 (하나라도 부족하면 전체 실패)
        for (OrderItemRequest item : items) {
            Product product = productMap.get(item.productId());
            if (!product.hasEnoughStock(item.quantity())) {
                throw new IllegalArgumentException("재고가 부족합니다.");
            }
        }

        // 3. 재고 차감
        for (OrderItemRequest item : items) {
            Product product = productMap.get(item.productId());
            product.decreaseStock(item.quantity());
            productRepository.save(product);
        }

        // 4. 주문 생성 (PENDING + 스냅샷)
        Order order = new Order(userId);
        for (OrderItemRequest item : items) {
            Product product = productMap.get(item.productId());
            order.addItem(new OrderItem(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    item.quantity()
            ));
        }
        order.applyDiscount(originalAmount, discountAmount);
        orderRepository.save(order);


        // 5. 결제 요청
        String paymentResult = paymentGateway.requestPayment(order.getId(), order.getTotalAmount());

        // 6. 결제 결과 처리
        if ("SUCCESS".equals(paymentResult)) {
            order.markPaid();
        } else {
            // 보상 트랜잭션: 재고 복구
            for (OrderItemRequest item : items) {
                Product product = productMap.get(item.productId());
                product.restoreStock(item.quantity());
                productRepository.save(product);
            }
            order.markFailed();
        }

        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("주문에 접근할 수 없습니다.");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> getMyOrders(Long userId) {
        return orderRepository.findAllByUserId(userId);
    }
}
