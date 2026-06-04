package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.AppliedCoupon;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PagePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;
    private final BrandService brandService;
    private final StockService stockService;
    private final UserCouponService userCouponService;

    /** 쿠폰 미적용 주문 생성. */
    @Transactional
    public OrderModel placeOrderPending(Long userId, PaymentMethod method, List<OrderLine> lines) {
        return placeOrderPending(userId, method, lines, null);
    }

    /**
     * 주문 생성(PENDING) — 각 상품의 활성 검증 + 스냅샷 + 재고 차감 + (선택)쿠폰 사용을 한 트랜잭션으로.
     * 재고 부족 시 CONFLICT, 쿠폰 검증/사용 실패 시 예외 → 전체 롤백 (차감/사용 전부 원복).
     * 쿠폰은 템플릿 ID(couponId)로 받아 사용 가능 발급분을 골라 사용하고 할인을 반영한다(UC-17).
     * 결제(PG)는 이 트랜잭션 밖에서 별도 호출한다 (01 §7.6).
     */
    @Transactional
    public OrderModel placeOrderPending(Long userId, PaymentMethod method, List<OrderLine> lines, Long couponId) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }
        OrderModel order = new OrderModel(userId, method);
        for (OrderLine line : lines) {
            ProductModel product = productService.getActiveProduct(line.productId());
            BrandModel brand = brandService.getActiveBrand(product.getBrandId());
            stockService.decrease(product.getId(), line.quantity());
            order.addItem(new OrderItem(
                    product.getId(),
                    product.getName(),
                    brand.getName(),
                    product.getImageUrl(),
                    new Money(product.getPrice()),
                    line.quantity()
            ));
        }
        order.calculateTotals();
        if (couponId != null) {
            AppliedCoupon applied = userCouponService.useForOrder(userId, couponId, order.getTotalAmount().getAmount());
            order.applyDiscount(applied.userCouponId(), new Money(applied.discountAmount()));
        }
        return orderRepository.save(order);
    }

    @Transactional
    public OrderModel markPaid(Long orderId) {
        OrderModel order = getOrder(orderId);
        order.markPaid();
        return orderRepository.save(order);
    }

    /** 결제 실패 처리 — 상태 전이 + 항목별 재고 원복 + (적용됐다면) 쿠폰 원복 (01 §7.6, UC-19). */
    @Transactional
    public OrderModel markFailed(Long orderId, String reason) {
        OrderModel order = getOrder(orderId);
        order.markFailed(reason);
        for (OrderItem item : order.getItems()) {
            stockService.increase(item.getProductId(), item.getQuantity());
        }
        if (order.getUserCouponId() != null) {
            userCouponService.restore(order.getUserCouponId());
        }
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long orderId) {
        return orderRepository.find(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + orderId + "] 주문을 찾을 수 없습니다."));
    }

    /** 본인 주문 목록 — 최신순 페이지 조회 (UC-09). */
    @Transactional(readOnly = true)
    public List<OrderModel> getMyOrders(Long userId, int page, int size) {
        PagePolicy.validate(page, size);
        return orderRepository.findByUserId(userId, page, size);
    }

    /** 전체 주문 모니터링 — 상태 필터(null=전체) + 최신순 페이지 (UC-12 Admin, §7.4 격리 예외). */
    @Transactional(readOnly = true)
    public List<OrderModel> getOrders(OrderStatus status, int page, int size) {
        PagePolicy.validate(page, size);
        return orderRepository.findAll(status, page, size);
    }
}
