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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderFacade {

    private final UserService userService;
    private final ProductService productService;
    private final StockService stockService;
    private final OrderService orderService;

    public OrderInfo placeOrder(Long userId, List<OrderLineCommand> lines) {
        validateInput(lines);
        userService.getById(userId);
        Map<Long, ProductModel> productMap = loadProducts(lines);

        List<OrderItem> items = buildItems(lines, productMap);
        long totalAmount = items.stream().mapToLong(OrderItem::subtotal).sum();

        OrderModel created = orderService.placeInitial(userId, totalAmount, items);
        try {
            stockService.decreaseAll(aggregateQuantities(lines));
            OrderModel succeeded = orderService.markSucceeded(created.getId());
            return OrderInfo.from(succeeded, items);
        } catch (CoreException e) {
            orderService.markFailed(created.getId(),
                e.getCustomMessage() != null ? e.getCustomMessage() : e.getMessage());
            throw e;
        }
    }

    private List<OrderItem> buildItems(List<OrderLineCommand> lines, Map<Long, ProductModel> productMap) {
        return lines.stream()
            .map(line -> {
                ProductModel product = productMap.get(line.productId());
                return new OrderItem(product.getId(), product.getName(), product.getPrice(), line.quantity());
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
            throw new CoreException(ErrorType.NOT_FOUND, "주문하려는 상품이 존재하지 않습니다.");
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
