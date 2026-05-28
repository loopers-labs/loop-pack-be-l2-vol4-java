package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductStockService {

    private final ProductStockRepository productStockRepository;

    @Transactional
    public ProductStockModel createStock(Long productId, Integer stock) {
        ProductStockModel model = ProductStockModel.of(productId, Stock.of(stock));
        return productStockRepository.save(model);
    }

    @Transactional(readOnly = true)
    public ProductStockModel getStock(Long productId) {
        return productStockRepository.findByProductId(productId)
                .orElseThrow(
                    () -> new CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")
                );
    }

    @Transactional
    public void changeStock(Long productId, Integer stock) {
        ProductStockModel model = getStock(productId);
        model.changeStock(Stock.of(stock));
    }

    @Transactional
    public void deleteStock(Long productId) {
        ProductStockModel model = getStock(productId);
        model.delete();
        productStockRepository.save(model);
    }
}