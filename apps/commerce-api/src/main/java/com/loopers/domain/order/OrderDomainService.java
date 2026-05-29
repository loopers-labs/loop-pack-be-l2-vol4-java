package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OrderDomainService {

    public OrderModel place(Long userId, Map<Long, Integer> quantitiesByProductId, Map<Long, ProductModel> productsById) {
        if (quantitiesByProductId == null || quantitiesByProductId.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 상품은 1개 이상이어야 합니다.");
        }
        List<OrderLine> orderLines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantitiesByProductId.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();
            ProductModel product = productsById.get(productId);
            if (product == null) {
                throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
            }
            product.decreaseStock(quantity);
            orderLines.add(OrderLine.create(product.getId(), product.getName(), product.getPrice(), quantity));
        }
        return OrderModel.create(userId, orderLines);
    }
}
