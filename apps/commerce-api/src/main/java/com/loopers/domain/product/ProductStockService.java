package com.loopers.domain.product;

import com.loopers.domain.product.vo.Price;
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
    public ProductStockModel decrease(Long stockId, int quantity) {
        ProductStockModel stock = get(stockId);
        stock.decrease(quantity);
        return stock;
    }

    @Transactional
    public ProductStockModel increase(Long stockId, int quantity) {
        ProductStockModel stock = get(stockId);
        stock.increase(quantity);
        return stock;
    }

    @Transactional(readOnly = true)
    public List<ProductStockModel> findAllByProductId(Long productId) {
        return productStockRepository.findAllByProductId(productId);
    }

    @Transactional
    public ProductStockModel addStock(ProductModel product, Price price, Integer quantity) {
        return productStockRepository.save(new ProductStockModel(product, price, quantity));
    }

    @Transactional
    public ProductStockModel updateStock(Long stockId, Long price, Integer stockQuantity) {
        ProductStockModel stock = get(stockId);
        if (price != null) stock.applyPriceTo(price);
        if (stockQuantity != null) stock.applyQuantityDelta(stockQuantity);
        return stock;
    }

}
