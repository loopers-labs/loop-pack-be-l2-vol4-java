package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductFilter;
import com.loopers.domain.product.ProductLikeViewModel;
import com.loopers.domain.product.ProductLikeViewRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductSort;
import com.loopers.domain.stock.StockModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private static final int CACHE_PAGE_SIZE = 20;

    private final ProductService productService;
    private final StockService stockService;
    private final BrandService brandService;
    private final ProductDomainService productDomainService;
    private final ProductLikeViewRepository productLikeViewRepository;
    private final ProductCacheService productCacheService;

    @Transactional
    public ProductInfo createProduct(String name, Long price, Long brandId, int stockQuantity) {
        BrandModel brand = brandService.getById(brandId);
        ProductModel product = productService.create(new ProductModel(name, price, brandId));
        stockService.create(new StockModel(product.getId(), stockQuantity));
        productLikeViewRepository.save(new ProductLikeViewModel(product.getId()));
        productCacheService.evictAllList();
        return ProductInfo.from(productDomainService.combineWithBrand(product, brand, 0, stockQuantity));
    }

    public ProductInfo getProduct(Long id) {
        return productCacheService.getDetail(id)
            .map(cached -> {
                Long price = productService.getById(id).getPrice();
                int stockQuantity = stockService.getByProductId(id).getQuantity();
                return new ProductInfo(cached.id(), cached.name(), price, cached.brandId(), cached.brandName(), cached.likeCount(), stockQuantity);
            })
            .orElseGet(() -> {
                ProductModel product = productService.getById(id);
                BrandModel brand = brandService.getById(product.getBrandId());
                int likeCount = productLikeViewRepository.findByProductId(id)
                    .map(ProductLikeViewModel::getLikeCount)
                    .orElse(0);
                int stockQuantity = stockService.getByProductId(id).getQuantity();
                productCacheService.putDetail(id, new ProductCacheItem(id, product.getName(), brand.getId(), brand.getName(), likeCount));
                return ProductInfo.from(productDomainService.combineWithBrand(product, brand, likeCount, stockQuantity));
            });
    }

    public Page<ProductInfo> getProducts(Long brandId, ProductSort sort, Long minPrice, Long maxPrice, Boolean inStock, int page, int size) {
        boolean cacheable = brandId == null && minPrice == null && maxPrice == null && inStock == null && page < 3 && size == CACHE_PAGE_SIZE;

        if (cacheable) {
            List<ProductInfo> cached = productCacheService.getList(sort, page).orElse(null);
            Long cachedTotal = productCacheService.getListTotal(sort, page).orElse(null);
            if (cached != null && cachedTotal != null) {
                return new PageImpl<>(cached, PageRequest.of(page, size), cachedTotal);
            }
        }

        ProductFilter filter = ProductFilter.of(brandId, minPrice, maxPrice, inStock);
        Page<ProductModel> products = productService.getAll(filter, sort, PageRequest.of(page, size));

        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        Map<Long, Integer> likeCountMap = productLikeViewRepository.findAllByProductIdIn(productIds)
            .stream()
            .collect(Collectors.toMap(ProductLikeViewModel::getProductId, ProductLikeViewModel::getLikeCount));

        Page<ProductInfo> result = products.map(product ->
            ProductInfo.from(product, likeCountMap.getOrDefault(product.getId(), 0)));

        if (cacheable) {
            productCacheService.putList(sort, page, result.getContent(), result.getTotalElements());
        }

        return result;
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, Long price) {
        ProductModel product = productService.update(id, name, price);
        BrandModel brand = brandService.getById(product.getBrandId());
        int likeCount = productLikeViewRepository.findByProductId(id)
            .map(ProductLikeViewModel::getLikeCount)
            .orElse(0);
        int stockQuantity = stockService.getByProductId(id).getQuantity();
        productCacheService.evictDetail(id);
        productCacheService.evictAllList();
        return ProductInfo.from(productDomainService.combineWithBrand(product, brand, likeCount, stockQuantity));
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.delete(id);
        productLikeViewRepository.deleteByProductId(id);
        productCacheService.evictDetail(id);
        productCacheService.evictAllList();
    }

    @Transactional
    public void updateStock(Long productId, int quantity) {
        productService.getById(productId);
        stockService.update(productId, quantity);
    }
}
