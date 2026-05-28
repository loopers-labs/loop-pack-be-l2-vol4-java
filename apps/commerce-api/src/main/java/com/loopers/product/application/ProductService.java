package com.loopers.product.application;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final ProductReader productReader;

    @Transactional(readOnly = true)
    public ProductResult.Detail get(Long productId) {
        Product product = productReader.getActive(productId);
        int stockQuantity = productReader.getStock(productId).getQuantity();
        return ProductResult.Detail.from(product, stockQuantity);
    }

    @Transactional(readOnly = true)
    public List<ProductResult.Detail> getAll(ProductSortOption sortOption) {
        List<Product> products = switch (sortOption) {
            case LATEST -> productRepository.findAllOnSaleOrderByLatest();
            case PRICE_ASC -> productRepository.findAllOnSaleOrderByPriceAsc();
            case LIKES_DESC -> productRepository.findAllOnSaleOrderByLikeCountDesc();
        };

        Map<Long, Integer> stockByProductId = productStockRepository
                .findAllByProductIdIn(products.stream().map(Product::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProductStock::getProductId, ProductStock::getQuantity));

        return products.stream()
                .map(product -> ProductResult.Detail.from(product, stockByProductId.getOrDefault(product.getId(), 0)))
                .toList();
    }
}
