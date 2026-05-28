package com.loopers.application.order;

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

    /**
     * 다중 도메인 합성이라 Facade @Transactional을 둔다:
     * (1) StockService와 OrderService를 한 비즈니스 단위로 묶어야 하고,
     * (2) 부분 성공(재고만 빠지고 주문은 안 만들어진 상태)은 데이터 비일관성이며,
     * (3) 외부 I/O는 없다 — 세 조건 모두 충족(CLAUDE.md 트랜잭션 규약).
     * 실패 복구, 동시성 제어는 다음 라운드 주제.
     */
    @Transactional
    public OrderInfo placeOrder(Long userId, List<OrderLineCommand> lines) {
        validateInput(lines);
        userService.getById(userId);
        Map<Long, ProductModel> productMap = loadProducts(lines);
        List<OrderItem> items = buildItems(lines, productMap);

        stockService.decreaseAll(aggregateQuantities(lines));
        OrderModel saved = orderService.place(userId, items);
        return OrderInfo.from(saved);
    }

    /** 같은 productId가 여러 line으로 와도 OrderItem은 line별로 1행씩 보존한다 (이력/표시 의도). 재고 차감만 {@link #aggregateQuantities}로 합산한다. */
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
