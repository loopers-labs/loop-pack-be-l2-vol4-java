package com.loopers.domain.product;

import com.loopers.domain.order.OrderItemInput;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductStockService {

    private final ProductStockRepository productStockRepository;

    @Transactional(readOnly = true)
    public ProductStockModel get(Long stockId) {
        return productStockRepository.findById(stockId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + stockId + "] 재고를 찾을 수 없습니다."));
    }

    @Transactional
    public List<ProductStockModel> decrease(List<OrderItemInput> inputs) {
        return inputs.stream()
                .map(input -> {
                    ProductStockModel stock = getForUpdate(input.stockId());
                    stock.decrease(input.quantity());
                    return stock;
                })
                .toList();
    }

    @Transactional
    public void restore(List<OrderItemModel> items) {
        items.forEach(item -> increase(item.getStockId(), item.getQuantity().getValue()));
    }

    @Transactional
    public ProductStockModel decrease(Long stockId, int quantity) {
        ProductStockModel stock = getForUpdate(stockId);
        stock.decrease(quantity);
        return stock;
    }

    @Transactional
    public ProductStockModel increase(Long stockId, int quantity) {
        ProductStockModel stock = getForUpdate(stockId);
        stock.increase(quantity);
        return stock;
    }

    private ProductStockModel getForUpdate(Long stockId) {
        return productStockRepository.findByIdForUpdate(stockId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + stockId + "] 재고를 찾을 수 없습니다."));
    }
}
