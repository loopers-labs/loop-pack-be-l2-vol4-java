package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import com.loopers.infrastructure.product.ProductCacheStore;
import com.loopers.infrastructure.product.ProductListCacheValue;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStatsService productStatsService;
    private final StockService stockService;
    private final ProductInfoAssembler productInfoAssembler;
    private final ProductCacheStore productCacheStore;
    private final ApplicationEventPublisher eventPublisher;

    public ProductInfo getProduct(Long id) {
        Optional<ProductInfo> cached = productCacheStore.findProduct(id);
        if (cached.isPresent()) {
            return cached.get();
        }
        ProductModel product = productService.getById(id);
        BrandModel brand = brandService.getById(product.getBrandId());
        StockModel stock = stockService.getByProductId(id);
        ProductStatsModel stats = productStatsService.getByProduct(product);
        ProductInfo info = ProductInfo.from(product, brand, stock, stats);
        productCacheStore.putProduct(id, info);
        return info;
    }

    public Page<ProductInfo> getProducts(Long brandId, Pageable pageable) {
        String listKey = productCacheStore.listKey(brandId, pageable.getSort().toString(), pageable.getPageNumber(), pageable.getPageSize());
        Optional<ProductListCacheValue> cached = productCacheStore.findList(listKey);
        if (cached.isPresent()) {
            ProductListCacheValue value = cached.get();
            return new PageImpl<>(value.content(), pageable, value.totalElements());
        }

        Page<ProductInfo> page = isLikesDesc(pageable)
            ? getProductsOrderByLikeCountDesc(brandId, pageable)
            : getProductsByLatestOrPrice(brandId, pageable);

        productCacheStore.putList(listKey, new ProductListCacheValue(page.getContent(), page.getTotalElements()));
        return page;
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.delete(id);
        stockService.delete(id);
        eventPublisher.publishEvent(new ProductCacheEvictEvent(List.of(id)));
    }

    public Page<ProductInfo> getProductsByBrandId(Long brandId, Pageable pageable) {
        BrandModel brand = brandService.getById(brandId);
        Page<ProductModel> productPage = productService.findAllByBrandId(brandId, pageable);
        Set<Long> productIds = productPage.getContent().stream().map(ProductModel::getId).collect(Collectors.toSet());
        Map<Long, StockModel> stockMap = stockService.getMapByProductIds(productIds);
        Map<Long, ProductStatsModel> statsMap = productStatsService.getMapByProductIds(productIds);
        List<ProductInfo> infos = productPage.getContent().stream()
            .map(p -> ProductInfo.from(p, brand, stockMap.get(p.getId()), statsMap.get(p.getId())))
            .toList();
        return new PageImpl<>(infos, pageable, productPage.getTotalElements());
    }

    public ProductAdminInfo getProductForAdmin(Long id) {
        ProductModel product = productService.getById(id);
        BrandModel brand = brandService.getById(product.getBrandId());
        StockModel stock = stockService.getByProductId(id);
        ProductStatsModel stats = productStatsService.getByProduct(product);
        return ProductAdminInfo.from(product, brand, stock, stats);
    }

    @Transactional
    public ProductAdminInfo createProductForAdmin(Long brandId, String name, BigDecimal price, Long stockQuantity) {
        BrandModel brand = brandService.getById(brandId);
        ProductModel product = productService.create(brandId, name, price);
        StockModel stock = stockService.create(product.getId(), stockQuantity);
        ProductStatsModel stats = productStatsService.getByProduct(product);
        return ProductAdminInfo.from(product, brand, stock, stats);
    }

    @Transactional
    public ProductAdminInfo updateProductForAdmin(Long id, String name, BigDecimal price, Long stockQuantity) {
        ProductModel product = productService.update(id, name, price);
        StockModel stock = stockService.update(id, stockQuantity);
        BrandModel brand = brandService.getById(product.getBrandId());
        ProductStatsModel stats = productStatsService.getByProduct(product);
        eventPublisher.publishEvent(new ProductCacheEvictEvent(List.of(id)));
        return ProductAdminInfo.from(product, brand, stock, stats);
    }

    private Page<ProductInfo> getProductsByLatestOrPrice(Long brandId, Pageable pageable) {
        Page<ProductModel> productPage = productService.findProducts(brandId, pageable);
        List<ProductInfo> infos = productInfoAssembler.toInfoList(productPage.getContent());
        return new PageImpl<>(infos, pageable, productPage.getTotalElements());
    }

    private Page<ProductInfo> getProductsOrderByLikeCountDesc(Long brandId, Pageable pageable) {
        Page<ProductStatsModel> statsPage;
        if (brandId == null) {
            // A3: product_stats 드라이빙 — 전체 상품 likeCount 내림차순
            statsPage = productStatsService.findPage(pageable);
        } else {
            // B3: 브랜드 product ID 필터링 후 stats 페이지네이션
            List<Long> productIds = productService.findAllByBrandId(brandId).stream()
                .map(ProductModel::getId)
                .toList();
            statsPage = productStatsService.findPageByProductIds(productIds, pageable);
        }
        List<Long> orderedIds = statsPage.getContent().stream()
            .map(stats -> stats.getProduct().getId())
            .toList();
        Map<Long, ProductModel> productMap = productService.findAllByIds(orderedIds).stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p));
        List<ProductModel> orderedProducts = orderedIds.stream()
            .map(productMap::get)
            .filter(Objects::nonNull)
            .toList();
        List<ProductInfo> infos = productInfoAssembler.toInfoList(orderedProducts);
        return new PageImpl<>(infos, pageable, statsPage.getTotalElements());
    }

    private boolean isLikesDesc(Pageable pageable) {
        return pageable.getSort().stream()
            .anyMatch(order -> order.getProperty().equals("likeCount") && order.isDescending());
    }
}
