package com.loopers.domain.order;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.product.Product;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 도메인 서비스 — 순수 POJO. Repository/인프라에 의존하지 않고,
 * 로드된 도메인 객체(Product, UserCoupon)들만 받아 여러 엔티티에 얽힌 규칙을 수행한다.
 * 규칙: 각 상품의 재고를 차감하고, 쿠폰을 사용 처리해 할인을 계산하며, 주문 시점 스냅샷으로 주문을 조립한다.
 */
@Component
public class OrderService {

    /** 쿠폰 없는 주문 조립. */
    public Order place(Long userId, List<OrderLine> lines) {
        return place(userId, lines, null, ZonedDateTime.now());
    }

    public Order place(Long userId, List<OrderLine> lines, UserCoupon userCoupon, ZonedDateTime now) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderItem> items = new ArrayList<>();
        for (OrderLine line : lines) {
            Product product = line.product();
            Quantity quantity = Quantity.of(line.quantity());
            product.decreaseStock(quantity);
            items.add(new OrderItem(
                product.getId(),
                product.getName(),
                Money.of(product.getPrice()),
                quantity
            ));
        }

        if (userCoupon == null) {
            return new Order(userId, items);
        }

        Money totalAmount = items.stream()
            .map(OrderItem::subtotal)
            .reduce(Money.ZERO, Money::plus);
        userCoupon.use(userId, now);                                 // 소유자/사용됨/만료 검증 + USED 전이
        Money discount = userCoupon.calculateDiscount(totalAmount);  // 최소 주문 금액 검증 + 할인 계산
        return new Order(userId, items, discount, userCoupon.getId());
    }
}
