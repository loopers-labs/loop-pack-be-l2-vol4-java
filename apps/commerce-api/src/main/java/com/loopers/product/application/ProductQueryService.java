package com.loopers.product.application;

import com.loopers.brand.application.BrandReader;
import com.loopers.like.application.LikeReader;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductSortOption;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.product.domain.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductQueryService {

    private final BrandReader brandReader;
    private final LikeReader likeReader;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;

    @Transactional(readOnly = true)
    public ProductResult.Detail getProduct(Long productId) {
        Product product = get(productId);
        int stockQuantity = getStock(productId).getQuantity();
        String brandName = brandReader.getName(product.getBrandId());
        long likeCount = likeReader.countActive(productId);
        return ProductResult.Detail.from(product, brandName, stockQuantity, likeCount);
    }

    @Transactional(readOnly = true)
    public List<ProductResult.Detail> getProducts(ProductSortOption sortOption) {
        List<Product> products = productRepository.findAllOnSale(sortOption);

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
                .map(product -> {
                    // 목록은 가용성 우선: 보조 데이터 결손은 기본값(재고 0, 브랜드명 null)으로 응답하되,
                    // 데이터 결함이 품절로 위장되지 않도록 warn 으로 드러낸다. 단건 조회는 NOT_FOUND 로 엄격 처리.
                    if (!stockByProductId.containsKey(product.getId()) || !brandNameById.containsKey(product.getBrandId())) {
                        log.warn("상품 목록 보조 데이터 누락 productId={} brandId={} stockMissing={} brandMissing={}",
                                product.getId(), product.getBrandId(),
                                !stockByProductId.containsKey(product.getId()),
                                !brandNameById.containsKey(product.getBrandId()));
                    }
                    return ProductResult.Detail.from(
                            product,
                            brandNameById.get(product.getBrandId()),
                            stockByProductId.getOrDefault(product.getId(), 0),
                            likeCountByProductId.getOrDefault(product.getId(), 0L));
                })
                .toList();
    }

    private Product get(Long productId) {
        return productRepository.findActiveById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductStock getStock(Long productId) {
        return productStockRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.STOCK_NOT_FOUND));
    }
}
