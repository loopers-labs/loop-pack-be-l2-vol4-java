package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import com.loopers.support.cache.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final StockService stockService;
    private final BrandService brandService;

    // 신규 상품은 목록을 불완전하게 만든다 → 목록 캐시 전체 evict (상세는 아직 캐시 없음). admin 작업이라 빈도 낮아 비용 작음.
    @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    @Transactional
    public ProductAdminInfo createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        BrandModel brand = brandService.getActive(brandId);
        ProductModel product = productService.createProduct(name, description, price, brandId);
        StockModel stockModel = stockService.create(product.getId(), stock);
        return ProductAdminInfo.from(product, stockModel, brand);
    }

    // sync=true: 같은 키 동시 미스를 1개만 DB 로 보내는 single-flight(스탬피드 방어). 단 JVM 로컬 락이라 단일 인스턴스에서만 유효.
    @Cacheable(cacheNames = CacheConfig.PRODUCT_CACHE, key = "#id", sync = true)
    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getActive(id);
        StockModel stockModel = stockService.getByProductId(id);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, stockModel.isPurchasable(), brand);
    }

    // 복합 키: null brandId 는 'all' sentinel 로. 정밀 evict 불가(어느 페이지에 있는지 모름) → TTL 로만 정합성 유지.
    @Cacheable(
        cacheNames = CacheConfig.PRODUCT_LIST_CACHE,
        key = "(#brandId == null ? 'all' : #brandId) + ':' + #sort + ':' + #page + ':' + #size",
        sync = true
    )
    @Transactional(readOnly = true)
    public List<ProductInfo> getAllProducts(Long brandId, ProductSortType sort, int page, int size) {
        List<ProductModel> products = productService.getAllActiveProducts(brandId, sort, page, size);
        Map<Long, StockModel> stockByProductId = stockByProductId(products);
        Map<Long, BrandModel> brandById = brandById(products);
        return products.stream()
            .map(product -> ProductInfo.from(
                product,
                stockByProductId.get(product.getId()).isPurchasable(),
                brandById.get(product.getBrandId())
            ))
            // ArrayList 로 수집: Stream.toList() 의 불변 ListN 은 GenericJackson2Json 직렬화의 @class 타입 id 라운드트립이 깨진다.
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductAdminInfo getProductForAdmin(Long id) {
        ProductModel product = productService.getProduct(id);
        StockModel stockModel = stockService.getByProductId(id);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductAdminInfo.from(product, stockModel, brand);
    }

    @Transactional(readOnly = true)
    public Page<ProductAdminInfo> getAllProductsForAdmin(Long brandId, int page, int size) {
        Page<ProductModel> products = productService.getAllProducts(brandId, PageRequest.of(page, size));
        Map<Long, StockModel> stockByProductId = stockByProductId(products.getContent());
        Map<Long, BrandModel> brandById = brandById(products.getContent());
        return products.map(product -> ProductAdminInfo.from(
            product,
            stockByProductId.get(product.getId()),
            brandById.get(product.getBrandId())
        ));
    }

    // name/price/stock 변경 → 상세 단일키 + 목록 전체 evict. (재고 차감 같은 빈번한 변경은 주문 경로라 여기 안 옴 → 캐시 안 깸.)
    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.PRODUCT_CACHE, key = "#id"),
        @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    @Transactional
    public ProductAdminInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, price);
        StockModel stockModel = stockService.getByProductId(id);
        stockModel.changeTo(stock);
        BrandModel brand = brandService.getBrand(product.getBrandId());
        return ProductAdminInfo.from(product, stockModel, brand);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = CacheConfig.PRODUCT_CACHE, key = "#id"),
        @CacheEvict(cacheNames = CacheConfig.PRODUCT_LIST_CACHE, allEntries = true)
    })
    @Transactional
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }

    private Map<Long, StockModel> stockByProductId(List<ProductModel> products) {
        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        return stockService.getAllByProductIdIn(productIds).stream()
            .collect(Collectors.toMap(StockModel::getProductId, Function.identity()));
    }

    private Map<Long, BrandModel> brandById(List<ProductModel> products) {
        List<Long> brandIds = products.stream().map(ProductModel::getBrandId).toList();
        return brandService.getAllByIdIn(brandIds).stream()
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));
    }
}
