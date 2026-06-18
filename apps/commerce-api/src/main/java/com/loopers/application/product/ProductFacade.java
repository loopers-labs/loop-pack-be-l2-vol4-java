package com.loopers.application.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final RedisTemplate<String, String> defaultRedisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        String cacheKey = "product:detail::" + id;
        try {
            String cachedJson = defaultRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, ProductInfo.class);
            }
        } catch (Exception e) {
            log.error("Redis read error for key: {}, falling back to DB", cacheKey, e);
        }

        ProductModel product = productRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
        BrandModel brand = brandRepository.findById(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        int likeCount = product.getLikeCount();
        ProductInfo productInfo = ProductInfo.from(product, brand.getName(), likeCount);

        try {
            String json = objectMapper.writeValueAsString(productInfo);
            defaultRedisTemplate.opsForValue().set(cacheKey, json, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis write error for key: {}", cacheKey, e);
        }

        return productInfo;
    }

    @Transactional(readOnly = true)
    public Page<ProductInfo> getProducts(Long brandId, String sort, Pageable pageable) {
        Page<ProductModel> productPage = productRepository.findAll(brandId, sort, pageable);

        List<Long> brandIds = productPage.getContent().stream()
                .map(ProductModel::getBrandId)
                .distinct()
                .toList();

        List<BrandModel> brands = brandRepository.findByIds(brandIds);
        Map<Long, String> brandNameMap = brands.stream()
                .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));

        return productPage.map(product -> {
            String brandName = brandNameMap.getOrDefault(product.getBrandId(), "알수없음");
            int likeCount = product.getLikeCount();
                    
            return ProductInfo.from(product, brandName, likeCount);
        });
    }

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
