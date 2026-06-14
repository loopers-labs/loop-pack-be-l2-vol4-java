package com.loopers.domain.order;

import com.loopers.domain.money.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductService productService;

    /**
     * 상품 행에 비관적 락을 걸고 재고를 차감한 뒤, 주문 스냅샷 항목을 만든다.
     */
    @Transactional
    public List<OrderItem> prepareItems(List<OrderLine> lines) {
        List<Long> productIds = lines.stream()
            .map(OrderLine::productId)
            .distinct()
            .toList();
        Map<Long, Product> products = productService.getProductsForUpdate(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        return lines.stream()
            .map(line -> {
                Product product = products.get(line.productId());
                product.decreaseStock(line.quantity());
                return new OrderItem(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    line.quantity()
                );
            })
            .toList();
    }

    @Transactional
    public Order complete(Long userId, List<OrderItem> items, Money discountAmount) {
        Order order = Order.place(userId, items, discountAmount);
        return orderRepository.save(order);
    }
}
