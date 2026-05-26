package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public StockModel createStock(Long productId, int initialQuantity) {
        StockModel stock = StockModel.of(productId, initialQuantity);
        return stockRepository.save(stock);
    }

    @Transactional(readOnly = true)
    public StockModel getStock(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
    }

    /**
     * 상품 ID 목록으로 재고를 일괄 조회한다. (N+1 회피용)
     */
    @Transactional(readOnly = true)
    public List<StockModel> getStocksByProductIds(List<Long> productIds) {
        return stockRepository.findAllByProductIdIn(productIds);
    }

    @Transactional
    public void deduct(Long productId, int quantity) {
        StockModel stock = getStock(productId);
        stock.deduct(quantity);
        stockRepository.save(stock);
    }

    @Transactional
    public void restore(Long productId, int quantity) {
        StockModel stock = getStock(productId);
        stock.restore(quantity);
        stockRepository.save(stock);
    }

    /**
     * 재고 수량을 절대값으로 변경한다. 어드민의 상품 수정 흐름에서 사용.
     */
    @Transactional
    public void changeQuantity(Long productId, int newQuantity) {
        StockModel stock = getStock(productId);
        stock.changeQuantity(newQuantity);
        stockRepository.save(stock);
    }
}
