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

import java.util.ArrayList;
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

    @Transactional
    public OrderInfo placeOrder(Long userId, List<OrderLineCommand> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }
        userService.getById(userId);

        List<Long> productIds = lines.stream().map(OrderLineCommand::productId).distinct().toList();
        List<ProductModel> products = productService.getAllByIds(productIds);
        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문하려는 상품이 존재하지 않습니다.");
        }
        Map<Long, ProductModel> productMap = products.stream()
            .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

        List<OrderItem> items = new ArrayList<>();
        for (OrderLineCommand line : lines) {
            ProductModel product = productMap.get(line.productId());
            stockService.decrease(line.productId(), line.quantity());
            items.add(new OrderItem(product.getId(), product.getName(), product.getPrice(), line.quantity()));
        }

        OrderModel saved = orderService.place(userId, items);
        return OrderInfo.from(saved);
    }
}
