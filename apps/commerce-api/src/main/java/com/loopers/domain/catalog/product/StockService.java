package com.loopers.domain.catalog.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StockService {

    private final ProductRepository productRepository;

    public StockService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Map<Long, Product> decrease(Collection<StockRequest> requests) {
        Map<Long, Product> products = getProductsForUpdate(requests);
        validateAllOrderable(requests, products);

        for (StockRequest request : requests) {
            Product product = products.get(request.productId());
            product.decreaseStock(request.quantity());
            products.put(product.getId(), productRepository.save(product));
        }

        return products;
    }

    public void restore(Collection<StockRequest> requests) {
        Map<Long, Product> products = getProductsForUpdate(requests);
        for (StockRequest request : requests) {
            Product product = products.get(request.productId());
            product.restoreStock(request.quantity());
            productRepository.save(product);
        }
    }

    private Map<Long, Product> getProductsForUpdate(Collection<StockRequest> requests) {
        List<Long> productIds = requests.stream()
            .map(StockRequest::productId)
            .distinct()
            .sorted()
            .toList();
        Map<Long, Product> products = productRepository.findAllByIdsForUpdate(productIds)
            .stream()
            .collect(Collectors.toMap(Product::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        for (Long productId : productIds) {
            if (!products.containsKey(productId)) {
                throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
            }
        }

        return products;
    }

    private void validateAllOrderable(Collection<StockRequest> requests, Map<Long, Product> products) {
        for (StockRequest request : requests) {
            Product product = products.get(request.productId());
            if (!product.isOnSale()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "판매 중인 상품만 주문할 수 있습니다.");
            }
            if (product.getStockQuantity() < request.quantity()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "상품 재고가 부족합니다.");
            }
        }
    }

    public record StockRequest(Long productId, Integer quantity) {
        public StockRequest {
            if (productId == null || productId <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
            }
            if (quantity == null || quantity <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
            }
        }
    }
}
