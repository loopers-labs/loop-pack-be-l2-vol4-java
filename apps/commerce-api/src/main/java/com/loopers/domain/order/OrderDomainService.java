package com.loopers.domain.order;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrderDomainService {

    public void validateStockAvailability(List<StockModel> stocks, Map<Long, Integer> quantityByProductId) {
        for (StockModel stock : stocks) {
            int required = quantityByProductId.get(stock.getProductId());
            if (!stock.isAvailable(required)) {
                throw new CoreException(ErrorType.BAD_REQUEST, "[productId = " + stock.getProductId() + "] 재고가 부족합니다.");
            }
        }
    }

    public long calculateTotalPrice(List<ProductModel> products, Map<Long, Integer> quantityByProductId) {
        return products.stream()
            .mapToLong(p -> p.getPrice() * quantityByProductId.getOrDefault(p.getId(), 0))
            .sum();
    }
}
