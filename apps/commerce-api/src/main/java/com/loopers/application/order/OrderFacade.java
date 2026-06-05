package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final StockService stockService;
    private final OrderService orderService;
    private final CouponService couponService;

    /**
     * 다중 도메인 합성이라 Facade @Transactional을 둔다:
     * (1) Stock·Coupon·Order Service를 한 비즈니스 단위로 묶어야 하고,
     * (2) 부분 성공(재고만 빠지고 주문은 안 만들어진 상태)은 데이터 비일관성이며,
     * (3) 외부 I/O는 없다 — 세 조건 모두 충족(CLAUDE.md 트랜잭션 규약).
     * 쿠폰 사용(@Version 충돌) 또는 재고 부족 시 전체가 롤백된다.
     */
    @Transactional
    public OrderInfo placeOrder(Long userId, List<OrderLineCommand> lines) {
        validateInput(lines);
        userService.getById(userId);
        Map<Long, ProductModel> productMap = loadProducts(lines);

        stockService.decreaseAll(aggregateQuantities(lines));
        List<OrderItem> items = buildItems(userId, lines, productMap);
        OrderModel saved = orderService.place(userId, items);
        return OrderInfo.from(saved);
    }

    /**
     * 항목별로 OrderItem을 만든다. 같은 productId가 여러 line으로 와도 line별로 1행씩 보존한다 (이력/표시 의도, 재고 차감만 {@link #aggregateQuantities}로 합산).
     * line에 couponId가 있으면 해당 항목 subtotal 기준으로 쿠폰을 사용 처리하고 할인액을 OrderItem에 스냅샷한다.
     */
    private List<OrderItem> buildItems(Long userId, List<OrderLineCommand> lines, Map<Long, ProductModel> productMap) {
        return lines.stream()
            .map(line -> {
                ProductModel product = productMap.get(line.productId());
                Money subtotal = product.getPrice().multiply(line.quantity());
                Money discount = line.couponId() == null
                    ? Money.ZERO
                    : couponService.use(userId, line.couponId(), subtotal);
                return new OrderItem(product.getId(), product.getName(), product.getPrice().value(), line.quantity(), line.couponId(), discount);
            })
            .toList();
    }

    private void validateInput(List<OrderLineCommand> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        long couponCount = lines.stream().filter(line -> line.couponId() != null).count();
        if (couponCount > 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 주문 1건당 1장만 적용할 수 있습니다.");
        }
    }

    private Map<Long, ProductModel> loadProducts(List<OrderLineCommand> lines) {
        List<Long> productIds = lines.stream().map(OrderLineCommand::productId).distinct().toList();
        List<ProductModel> products = productService.getAllByIds(productIds);
        if (products.size() != productIds.size()) {
            Set<Long> found = products.stream().map(ProductModel::getId).collect(Collectors.toSet());
            List<Long> missing = productIds.stream().filter(id -> !found.contains(id)).toList();
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품: " + missing);
        }
        return products.stream().collect(Collectors.toMap(ProductModel::getId, Function.identity()));
    }

    private Map<Long, Integer> aggregateQuantities(List<OrderLineCommand> lines) {
        return lines.stream().collect(Collectors.toMap(
            OrderLineCommand::productId,
            OrderLineCommand::quantity,
            Integer::sum
        ));
    }
}
