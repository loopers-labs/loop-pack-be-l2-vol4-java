package com.loopers.product.application;

import com.loopers.brand.application.BrandReader;
import com.loopers.like.application.LikeReader;
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
public class ProductQueryService {

    private final ProductReader productReader;
    private final BrandReader brandReader;
    private final LikeReader likeReader;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;

    @Transactional(readOnly = true)
    public ProductResult.Detail get(Long productId) {
        Product product = productReader.getActive(productId);
        int stockQuantity = productReader.getStock(productId).getQuantity();
        String brandName = brandReader.get(product.getBrandId()).getName();
        long likeCount = likeReader.countActive(productId);
        return ProductResult.Detail.from(product, brandName, stockQuantity, likeCount);
    }

    @Transactional(readOnly = true)
    public List<ProductResult.Detail> getAll(ProductSortOption sortOption) {
        List<Product> products = switch (sortOption) {
            case LATEST -> productRepository.findAllOnSaleOrderByLatest();
            case PRICE_ASC -> productRepository.findAllOnSaleOrderByPriceAsc();
            case LIKES_DESC -> productRepository.findAllOnSaleOrderByLikeCountDesc();
        };

        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(Product::getId).toList();
        List<Long> brandIds = products.stream().map(Product::getBrandId).distinct().toList();

        Map<Long, Integer> stockByProductId = productStockRepository.findAllByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductStock::getProductId, ProductStock::getQuantity));
        Map<Long, String> brandNameById = brandReader.getNames(brandIds);
        Map<Long, Long> likeCountByProductId = likeReader.countActiveByProductIds(productIds);

        return products.stream()
                .map(product -> ProductResult.Detail.from(
                        product,
                        brandNameById.get(product.getBrandId()),
                        stockByProductId.getOrDefault(product.getId(), 0),
                        likeCountByProductId.getOrDefault(product.getId(), 0L)))
                .toList();
    }
}
