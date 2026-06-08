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

import java.util.Comparator;
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
        // 1) 상품/브랜드 검증 + 주문 항목 구성 (요청 순서 — 검증 에러 우선순위 보존)
        for (OrderLine line : lines) {
            ProductModel product = productService.getActiveProduct(line.productId());
            BrandModel brand = brandService.getActiveBrand(product.getBrandId());
            order.addItem(new OrderItem(
                    product.getId(),
                    product.getName(),
                    brand.getName(),
                    product.getImageUrl(),
                    new Money(product.getPrice()),
                    line.quantity()
            ));
        }
        // 2) 재고 차감 — 여러 상품의 락을 항상 productId 오름차순으로 획득해 데드락을 방지한다.
        //    (두 주문이 겹치는 상품을 서로 다른 순서로 잠그면 상호 대기 → 데드락)
        lines.stream()
                .sorted(Comparator.comparingLong(OrderLine::productId))
                .forEach(line -> stockService.decrease(line.productId(), line.quantity()));
        order.calculateTotals();
        if (couponId != null) {
            AppliedCoupon applied = userCouponService.useForOrder(userId, couponId, order.getTotalAmount().getAmount());
            order.applyDiscount(applied.userCouponId(), new Money(applied.discountAmount()));
        }
        return orderRepository.save(order);
    }

    @Transactional
    public OrderModel markPaid(Long orderId) {
        OrderModel order = getOrderForUpdate(orderId);
        order.markPaid();
        return orderRepository.save(order);
    }

    /** 결제 실패 처리 — 상태 전이 + 항목별 재고 원복 + (적용됐다면) 쿠폰 원복 (01 §7.6, UC-19). */
    @Transactional
    public OrderModel markFailed(Long orderId, String reason) {
        OrderModel order = getOrderForUpdate(orderId);
        order.markFailed(reason);
        // 재고 원복도 productId 오름차순으로 락을 잡아 차감(placeOrderPending)과 동일한 락 순서를 유지(데드락 방지).
        order.getItems().stream()
                .sorted(Comparator.comparingLong(OrderItem::getProductId))
                .forEach(item -> stockService.increase(item.getProductId(), item.getQuantity()));
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

    /**
     * 결제 결과 반영용 주문 로드 — 비관적 락(FOR UPDATE)으로 행을 잠근다.
     * 동시 확정(reconcile/콜백)이 같은 주문에 들어와도 선행 트랜잭션 커밋까지 대기 → 재조회 시
     * 이미 PAID/FAILED라 OrderModel.requirePending()이 CONFLICT를 던져 이중 보상을 막는다.
     * markPaid/markFailed 전용 (쓰기 트랜잭션 안에서 호출).
     */
    private OrderModel getOrderForUpdate(Long orderId) {
        return orderRepository.findForUpdate(orderId)
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
