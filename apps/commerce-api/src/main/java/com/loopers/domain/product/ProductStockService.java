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
        boolean decreased = productStockRepository.decreaseIfSufficient(stockId, quantity);
        if (!decreased) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        return get(stockId);
    }

    @Transactional
    public void increase(Long stockId, int quantity) {
        productStockRepository.increaseStock(stockId, quantity);
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
    public ProductStockModel updateStock(Long productId, Long stockId, Long price, Integer stockQuantity) {
        ProductStockModel stock = get(stockId);
        if (!stock.getProduct().getId().equals(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + stockId + "] 해당 상품의 재고를 찾을 수 없습니다.");
        }
        if (price != null || stockQuantity != null) {
            boolean updated = productStockRepository.updateStockAttributes(productId, stockId, price, stockQuantity);
            if (!updated && stockQuantity != null) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
            }
        }
        return get(stockId);
    }

}
