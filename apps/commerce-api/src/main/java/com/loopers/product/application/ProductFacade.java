package com.loopers.product.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.brand.domain.BrandService;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductService;
import com.loopers.product.domain.SortCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final BrandService brandService;

    @Transactional
    public ProductInfo createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        if (brandId != null) {
            brandService.getOrThrow(brandRepository.find(brandId));
        }
        ProductModel product = new ProductModel(name, description, price, stock, brandId);
        return ProductInfo.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long productId) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        String brandName = null;
        if (product.getBrandId() != null) {
            brandName = brandRepository.find(product.getBrandId())
                .map(BrandModel::getName)
                .orElse(null);
        }
        return ProductInfo.from(product, brandName);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(SortCondition sort, Long brandId, int page, int size) {
        List<ProductModel> products = productRepository.findAll(sort, brandId, page, size);

        // N+1 방지 — brandId 목록으로 IN 쿼리 일괄 조회 (결정 10 참고)
        List<Long> brandIds = products.stream()
            .map(ProductModel::getBrandId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<Long, String> brandNameMap = brandRepository.findAllByIds(brandIds)
            .stream()
            .collect(Collectors.toMap(BrandModel::getId, BrandModel::getName));

        return products.stream()
            .map(p -> ProductInfo.from(p, brandNameMap.get(p.getBrandId())))
            .toList();
    }

    @Transactional
    public ProductInfo updateProduct(Long productId, String name, String description, Long price, Integer stock) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        product.update(name, description, price, stock);
        return ProductInfo.from(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long productId) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        product.delete();
        productRepository.save(product);
    }
}
