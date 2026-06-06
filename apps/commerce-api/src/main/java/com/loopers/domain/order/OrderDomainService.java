package com.loopers.domain.order;

import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrderDomainService {

    /**
     * 주문을 생성한다 — 상품·재고를 검증하고(쿠폰 지정 시 본인 소유·사용 가능까지),
     * 모두 통과하면 재고를 차감하고 항목 스냅샷·할인액을 확정한 Order 를 만든다.
     * 쿠폰 검증은 재고 차감(상태 변경) 전에 수행해 거부 시 아무것도 변경되지 않게 한다(AC-07-7).
     * {@code userCoupon} 이 null 이면 할인액은 0이다. 실제 사용 처리(USED 전이)는 주문 저장 후 호출자가 한다.
     */
    public Order create(Long userId,
                        List<Product> products,
                        List<OrderCommand.OrderLine> rawLines,
                        UserCoupon userCoupon,
                        ZonedDateTime now) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문자는 비어있을 수 없습니다.");
        }

        OrderLines lines = OrderLines.from(rawLines);
        Map<Long, Product> productById = (products == null ? List.<Product>of() : products).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> missing = lines.productIds().stream()
                .filter(id -> !productById.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품: " + missing);
        }

        List<Long> shortages = lines.productIds().stream()
                .filter(id -> !productById.get(id).hasEnoughStock(lines.quantityOf(id)))
                .toList();
        if (!shortages.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족한 상품: " + shortages);
        }

        if (userCoupon != null) {
            if (!userCoupon.isOwnedBy(userId)) {
                throw new CoreException(ErrorType.FORBIDDEN, "본인 소유의 쿠폰이 아닙니다.");
            }
            if (!userCoupon.isUsable(now)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
            }
        }

        List<OrderItem> items = new ArrayList<>();
        for (Long productId : lines.productIds()) {
            Product product = productById.get(productId);
            int qty = lines.quantityOf(productId);
            product.decreaseStock(qty);
            items.add(OrderItem.of(product.getId(), product.getName(), product.getPrice(), qty));
        }

        Money original = items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.of(0), Money::add);
        Money discount = userCoupon == null
                ? Money.of(0)
                : userCoupon.calculateDiscount(original);

        return Order.create(userId, items, discount);
    }
}
