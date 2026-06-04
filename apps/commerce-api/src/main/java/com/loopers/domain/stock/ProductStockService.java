package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductStockService {

    private final ProductStockRepository productStockRepository;

    @Transactional
    public ProductStock createProductStock(Long productId, int quantity) {
        ProductStock productStock = ProductStock.create(productId, quantity);
        return productStockRepository.save(productStock);
    }

    @Transactional(readOnly = true)
    public ProductStock getProductStock(Long productId) {
        return productStockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품 재고입니다."));
    }

    @Transactional(readOnly = true)
    public Map<Long, ProductStock> getProductStocks(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        List<ProductStock> productStocks = productStockRepository.findAllByProductIds(productIds);
        return productStocks.stream()
            .collect(Collectors.toMap(ProductStock::getProductId, Function.identity()));
    }

    @Transactional
    public List<ProductStock> getProductStocksForUpdate(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productStockRepository.findAllByProductIdsForUpdate(productIds);
    }

    @Transactional
    public void deduct(List<StockDeduction> deductions) {
        if (deductions.isEmpty()) {
            return;
        }
        List<Long> productIds = deductions.stream()
            .map(StockDeduction::productId)
            .toList();
        Map<Long, ProductStock> productStocks = getProductStocksForUpdate(productIds).stream()
            .collect(Collectors.toMap(ProductStock::getProductId, Function.identity()));
        if (productStocks.size() != Set.copyOf(productIds).size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품 재고입니다.");
        }
        deductions.forEach(deduction ->
            productStocks.get(deduction.productId()).deduct(deduction.quantity()));
    }

    @Transactional
    public ProductStock changeProductStock(Long productId, int quantity) {
        ProductStock productStock = getProductStock(productId);
        productStock.changeQuantity(quantity);
        return productStock;
    }
}
