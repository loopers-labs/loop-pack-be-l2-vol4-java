package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    public OrderModel createOrder(Long userId, List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 1개 이상이어야 합니다.");
        }

        List<OrderItemModel> items = new ArrayList<>();
        for (OrderLine line : lines) {
            ProductModel product = productRepository.find(line.productId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                    "[id = " + line.productId() + "] 상품을 찾을 수 없습니다."));
            Quantity quantity = Quantity.of(line.quantity());
            product.decreaseStock(quantity);
            productRepository.save(product);
            items.add(new OrderItemModel(
                line.productId(),                 // 조회 키 = 상품 id (미저장 엔티티 getId()=0L 회피)
                product.getName(),
                Money.of(product.getPrice()),
                quantity
            ));
        }

        OrderModel order = new OrderModel(userId, items);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long id) {
        return orderRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 주문을 찾을 수 없습니다."));
    }
}
