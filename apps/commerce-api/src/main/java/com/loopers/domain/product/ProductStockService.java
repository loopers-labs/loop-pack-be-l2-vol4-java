package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductStockService {

    private final ProductStockRepository productStockRepository;

    @Transactional
    public ProductStock createStock(Long productId, int initialStock) {
        return productStockRepository.save(new ProductStock(productId, initialStock));
    }

    @Transactional(readOnly = true)
    public ProductStock getStock(Long productId) {
        return productStockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
    }

    /**
     * 비관적 락으로 재고를 조회한 후 차감한다.
     * 동시에 여러 주문이 들어와도 한 번에 하나씩 순서대로 처리된다.
     *
     * 흐름:
     * A, B 동시 진입 (stock=1)
     * A: FOR UPDATE 락 획득 → stock 확인(1) → decrease(1) → stock=0 → 커밋 → 락 해제
     * B: 락 대기 → 락 획득 → stock 확인(0) → decrease 실패 → 예외
     */
    @Transactional
    public void decreaseStock(Long productId, int quantity) {
        ProductStock stock = productStockRepository.findByProductIdWithLock(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
        stock.decrease(quantity);           // 도메인 레벨에서 음수 방지
        productStockRepository.save(stock);
    }

    @Transactional
    public void restoreStock(Long productId, int quantity) {
        ProductStock stock = productStockRepository.findByProductIdWithLock(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
        stock.restore(quantity);
        productStockRepository.save(stock);
    }
}
