package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.coupon.UserCouponService.AppliedCoupon;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 주문 생성 도메인 서비스 — Coupon, Product, Brand, Order 의 다중 Aggregate 협력을 한 트랜잭션으로 묶는다.
 *
 * <h3>처리 순서 (한 트랜잭션 내부)</h3>
 * <ol>
 *   <li>입력 라인을 productId 오름차순으로 정렬 — 다중 상품 재고 차감 시 데드락 회피</li>
 *   <li>각 라인별 상품 조회(비관적 락) + 스냅샷 추출 + 재고 차감</li>
 *   <li>OrderItem 들로 Order Aggregate 를 구성 (status=PENDING, finalPrice=originalPrice)</li>
 *   <li>userCouponId 가 있으면 쿠폰 사용 처리 (비관적 락 + AVAILABLE→USED 전이) → 할인액 적용</li>
 *   <li>Order 저장</li>
 * </ol>
 *
 * <p>실패 시 모든 작업이 롤백된다 — 쿠폰은 USED 로 전이되지 않고, 재고도 차감되지 않은 상태로 복원된다.
 * <p>결제 외부 호출은 본 주차에서 다루지 않으며, 후주차에서 트랜잭션 분리·보상 트랜잭션과 함께 도입된다.
 */
@RequiredArgsConstructor
@Component
public class OrderCreationService {

    private final ProductService productService;
    private final BrandService brandService;
    private final OrderRepository orderRepository;
    private final UserCouponService userCouponService;

    @Transactional
    public OrderModel create(Long userId, List<OrderLine> lines, Long userCouponId) {
        if (userId == null || userId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 양수여야 합니다.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1 개 이상이어야 합니다.");
        }

        List<OrderLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(OrderLine::productId));

        List<OrderItemModel> items = new ArrayList<>(sorted.size());
        for (OrderLine line : sorted) {
            ProductModel product = productService.decreaseStock(line.productId(), line.quantity());
            BrandModel brand = brandService.getBrand(product.getBrandId());

            items.add(new OrderItemModel(
                product.getId(),
                product.getName(),
                brand.getName(),
                product.getPrice(),
                product.getImageUrl(),
                line.quantity()
            ));
        }

        OrderModel order = new OrderModel(userId, items);

        if (userCouponId != null) {
            AppliedCoupon applied = userCouponService.useForOrder(userId, userCouponId, order.getOriginalPrice());
            order.applyCoupon(applied.userCouponId(), applied.discountAmount());
        }

        return orderRepository.save(order);
    }
}
