package com.loopers.product.application;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductSortOption;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.product.domain.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductReader {

    private final ProductRepository productRepository;

    public void ensureActiveExists(Long productId) {
        if (!productRepository.existsActiveById(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    public ProductInfo getInfo(Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.PRODUCT_NOT_FOUND));
        return toInfo(product);
    }

    /** 여러 상품 요약을 한 번에 조회한다(랭킹 조합용). 없는 id 는 결과에서 빠진다. */
    public Map<Long, ProductInfo> getInfos(List<Long> ids) {
        return productRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, this::toInfo));
    }

    /** 좋아요 많은 순 상위 상품(랭킹 폴백용). 좋아요순으로 정렬된 Map 을 돌려준다. */
    public Map<Long, ProductInfo> getTopByLikes(int limit) {
        Map<Long, ProductInfo> result = new LinkedHashMap<>();
        for (Product product : productRepository.findAllOnSale(null, ProductSortOption.LIKES_DESC, 0, limit)) {
            result.put(product.getId(), toInfo(product));
        }
        return result;
    }

    private ProductInfo toInfo(Product product) {
        return new ProductInfo(product.getName(), product.getBrandId(), product.getPrice().value());
    }
}
