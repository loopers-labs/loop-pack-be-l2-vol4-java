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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final StockService stockService;
    private final BrandService brandService;
    private final ProductDomainService productDomainService;
    private final ProductLikeViewRepository productLikeViewRepository;

    @Transactional
    public ProductInfo createProduct(String name, Long price, Long brandId, int stockQuantity) {
        brandService.getById(brandId);
        ProductModel product = productService.create(new ProductModel(name, price, brandId));
        stockService.create(new StockModel(product.getId(), stockQuantity));
        productLikeViewRepository.save(new ProductLikeViewModel(product.getId()));
        return ProductInfo.from(product, 0);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getById(id);
        BrandModel brand = brandService.getById(product.getBrandId());
        int likeCount = productLikeViewRepository.findByProductId(id)
            .map(ProductLikeViewModel::getLikeCount)
            .orElse(0);
        int stockQuantity = stockService.getByProductId(id).getQuantity();
        return ProductInfo.from(productDomainService.combineWithBrand(product, brand, likeCount, stockQuantity));
    }

    public Page<ProductInfo> getProducts(Long brandId, ProductSort sort, Long minPrice, Long maxPrice, Boolean inStock, int page, int size) {
        ProductFilter filter = ProductFilter.of(brandId, minPrice, maxPrice, inStock);
        Page<ProductModel> products = productService.getAll(filter, sort, PageRequest.of(page, size));

        List<Long> productIds = products.stream().map(ProductModel::getId).toList();
        Map<Long, Integer> likeCountMap = productLikeViewRepository.findAllByProductIdIn(productIds)
            .stream()
            .collect(Collectors.toMap(ProductLikeViewModel::getProductId, ProductLikeViewModel::getLikeCount));

        return products.map(product ->
            ProductInfo.from(product, likeCountMap.getOrDefault(product.getId(), 0)));
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, Long price) {
        ProductModel product = productService.update(id, name, price);
        int likeCount = productLikeViewRepository.findByProductId(id)
            .map(ProductLikeViewModel::getLikeCount)
            .orElse(0);
        return ProductInfo.from(product, likeCount);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productService.delete(id);
        productLikeViewRepository.deleteByProductId(id);
    }

    @Transactional
    public void updateStock(Long productId, int quantity) {
        productService.getById(productId);
        stockService.update(productId, quantity);
    }
}
