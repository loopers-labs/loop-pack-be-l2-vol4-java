package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;

    @Transactional
    public void decreaseStocks(List<StockRequest> requests) {
        for (StockRequest request : requests) {
            ProductModel product = productRepository.findById(request.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
            
            if (product.getStock() == null) {
                throw new CoreException(ErrorType.STOCK_NOT_FOUND);
            }
            
            product.getStock().decrease(request.quantity());
        }
    }

    @Transactional
    public void decreaseStocksWithLock(List<StockRequest> requests) {
        // 데드락 방지를 위해 상품 ID 오름차순 정렬 후 순차적 락 획득
        List<StockRequest> sortedRequests = requests.stream()
                .sorted(java.util.Comparator.comparing(StockRequest::productId))
                .toList();

        for (StockRequest request : sortedRequests) {
            ProductModel product = productRepository.findByIdWithLock(request.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
            
            if (product.getStock() == null) {
                throw new CoreException(ErrorType.STOCK_NOT_FOUND);
            }
            
            product.getStock().decrease(request.quantity());
        }
    }

    @Transactional
    public void increaseStocks(List<StockRequest> requests) {
        // 데드락 방지를 위해 상품 ID 오름차순 정렬 후 순차적 락 획득
        List<StockRequest> sortedRequests = requests.stream()
                .sorted(java.util.Comparator.comparing(StockRequest::productId))
                .toList();

        for (StockRequest request : sortedRequests) {
            ProductModel product = productRepository.findByIdWithLock(request.productId())
                    .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
            
            if (product.getStock() == null) {
                throw new CoreException(ErrorType.STOCK_NOT_FOUND);
            }
            
            product.getStock().increase(request.quantity());
        }
    }

    public record StockRequest(Long productId, int quantity) {}
}
