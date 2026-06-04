package com.loopers.domain.order;

import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OrderDomainService {

    public Order create(Long userId,
                        List<Product> products,
                        List<OrderCommand.OrderLine> rawLines) {
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

        List<OrderItem> items = new ArrayList<>();
        for (Long productId : lines.productIds()) {
            Product product = productById.get(productId);
            int qty = lines.quantityOf(productId);
            product.decreaseStock(qty);
            items.add(OrderItem.of(product.getId(), product.getName(), product.getPrice(), qty));
        }

        return Order.create(userId, items);
    }
}
