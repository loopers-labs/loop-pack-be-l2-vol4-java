package com.loopers.application.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandEntity;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.InventoryEntity;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ProductApplicationService {

    private static final String CACHE_PREFIX = "products:list::";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final InventoryRepository inventoryRepository;
    private final LikeRepository likeRepository;
    private final ProductQueryRepository productQueryRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProductInfo createProduct(String brandId, String name, String description, Long price, Integer quantity) {
        BrandEntity brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        ProductEntity product = productRepository.save(new ProductEntity(brandId, name, description, price));
        InventoryEntity inventory = inventoryRepository.save(new InventoryEntity(product.getId(), quantity));
        return ProductInfo.from(product, brand, inventory);
    }

    public ProductInfo getProduct(String id) {
        return assembleProductInfo(findProductOrThrow(id));
    }

    public Page<ProductInfo> getAllProducts(String brandId, Pageable pageable) {
        if (pageable.getPageNumber() == 0) {
            return getPage0WithCache(brandId, pageable);
        }
        return queryFromDb(brandId, pageable);
    }

    @Transactional
    public void updateProduct(String id, String name, String description, Long price, Integer quantity) {
        ProductEntity product = findProductOrThrow(id);
        product.update(name, description, price);
        productRepository.save(product);

        InventoryEntity inventory = inventoryRepository.findByProductId(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + id + "] 재고를 찾을 수 없습니다."));
        inventory.updateQuantity(quantity);
        inventoryRepository.save(inventory);
    }

    @Transactional
    public void deleteProduct(String id) {
        ProductEntity product = findProductOrThrow(id);
        product.delete();
        productRepository.save(product);
        inventoryRepository.deleteByProductId(id);
        likeRepository.deleteAllByProductId(id);
    }

    private Page<ProductInfo> getPage0WithCache(String brandId, Pageable pageable) {
        String key = buildCacheKey(brandId, pageable);

        // Look-aside: 캐시 조회
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ProductListCache.class).toPage(pageable);
            } catch (JsonProcessingException e) {
                log.warn("캐시 역직렬화 실패, DB 조회로 fallback. key={}", key, e);
            }
        }

        // 캐시 미스: DB 조회 후 캐시 저장
        Page<ProductInfo> result = queryFromDb(brandId, pageable);
        try {
            String json = objectMapper.writeValueAsString(ProductListCache.from(result));
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("캐시 직렬화 실패, 캐싱 생략. key={}", key, e);
        }

        return result;
    }

    private Page<ProductInfo> queryFromDb(String brandId, Pageable pageable) {
        // [Batch] IN 쿼리 배치 조회 방식 (3 쿼리)
        Page<ProductEntity> products = productRepository.findAll(brandId, pageable);

        List<String> brandIds = products.stream().map(ProductEntity::getBrandId).distinct().toList();
        List<String> productIds = products.stream().map(ProductEntity::getId).toList();

        Map<String, BrandEntity> brandMap = brandRepository.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(BrandEntity::getId, Function.identity()));
        Map<String, InventoryEntity> inventoryMap = inventoryRepository.findAllByProductIds(productIds).stream()
                .collect(Collectors.toMap(InventoryEntity::getProductId, Function.identity()));

        return products.map(product -> {
            BrandEntity brand = Optional.ofNullable(brandMap.get(product.getBrandId()))
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
            InventoryEntity inventory = Optional.ofNullable(inventoryMap.get(product.getId()))
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + product.getId() + "] 재고를 찾을 수 없습니다."));
            return ProductInfo.from(product, brand, inventory);
        });

        // [JOIN] QueryDSL 단일 쿼리 방식 — 비교 테스트용
        // return productQueryRepository.findAllWithDetails(brandId, pageable);
    }

    private String buildCacheKey(String brandId, Pageable pageable) {
        String brandPart = brandId != null ? brandId.toString() : "all";
        return CACHE_PREFIX + brandPart + "::" + pageable.getSort().toString();
    }

    private ProductInfo assembleProductInfo(ProductEntity product) {
        BrandEntity brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
        InventoryEntity inventory = inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + product.getId() + "] 재고를 찾을 수 없습니다."));
        return ProductInfo.from(product, brand, inventory);
    }

    private ProductEntity findProductOrThrow(String id) {
        return productRepository.find(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }
}
