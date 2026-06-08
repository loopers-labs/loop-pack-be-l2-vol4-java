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

    @Transactional
    public OrderInfo placeOrder(Long userId, List<OrderLineCommand> lines, Long couponId) {
        validateInput(lines);
        userService.getById(userId);
        Map<Long, ProductModel> productMap = loadProducts(lines);

        stockService.decreaseAll(aggregateQuantities(lines));
        List<OrderItem> items = buildItems(lines, productMap);
        Money totalAmount = items.stream().map(OrderItem::subtotal).reduce(Money.ZERO, Money::add);
        Money discount = couponId == null ? Money.ZERO : couponService.use(userId, couponId, totalAmount);

        OrderModel saved = orderService.place(userId, items, couponId, discount);
        return OrderInfo.from(saved);
    }

    private List<OrderItem> buildItems(List<OrderLineCommand> lines, Map<Long, ProductModel> productMap) {
        return lines.stream()
            .map(line -> {
                ProductModel product = productMap.get(line.productId());
                return new OrderItem(product.getId(), product.getName(), product.getPrice().value(), line.quantity());
            })
            .toList();
    }

    private void validateInput(List<OrderLineCommand> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
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
