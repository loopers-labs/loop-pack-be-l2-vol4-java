package com.loopers.product.application;

import com.loopers.brand.domain.BrandModel;
import com.loopers.brand.domain.BrandRepository;
import com.loopers.brand.domain.BrandService;
import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductService;
import com.loopers.product.domain.SortCondition;
import com.loopers.stock.domain.StockModel;
import com.loopers.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final BrandService brandService;
    private final StockRepository stockRepository;

    @Transactional
    public ProductInfo createProduct(String name, String description, Long price, Integer initialStock, Long brandId) {
        if (brandId != null) {
            brandService.getOrThrow(brandRepository.find(brandId));
        }
        ProductModel product = productRepository.save(new ProductModel(name, description, price, brandId));
        StockModel stock = stockRepository.save(new StockModel(product.getId(), initialStock));
        return ProductInfo.from(product, stock.availableStock());
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long productId) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        Optional<BrandModel> brand = product.getBrandId() != null
            ? brandRepository.find(product.getBrandId())
            : Optional.empty();
        Integer availableStock = stockRepository.findByProductId(productId)
            .map(StockModel::availableStock)
            .orElse(0);
        return ProductInfo.from(product, productService.resolveBrandName(brand), availableStock);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(SortCondition sort, Long brandId, boolean inStock, int page, int size) {
        List<ProductModel> products = productRepository.findAll(sort, brandId, inStock, page, size);

        List<Long> productIds = products.stream()
            .map(ProductModel::getId)
            .toList();

        Map<Long, Integer> stockMap = stockRepository.findAllByProductIds(productIds).stream()
            .collect(Collectors.toMap(StockModel::getProductId, StockModel::availableStock));

        List<Long> brandIds = products.stream()
            .map(ProductModel::getBrandId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<Long, BrandModel> brandMap = brandRepository.findAllByIds(brandIds)
            .stream()
            .collect(Collectors.toMap(BrandModel::getId, Function.identity()));

        return products.stream()
            .map(p -> ProductInfo.from(
                p,
                productService.resolveBrandName(Optional.ofNullable(brandMap.get(p.getBrandId()))),
                stockMap.getOrDefault(p.getId(), 0)
            ))
            .toList();
    }

    @Transactional
    public ProductInfo updateProduct(Long productId, String name, String description, Long price) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        product.update(name, description, price);
        productRepository.save(product);
        Integer availableStock = stockRepository.findByProductId(productId)
            .map(StockModel::availableStock)
            .orElse(0);
        return ProductInfo.from(product, availableStock);
    }

    @Transactional
    public void deleteProduct(Long productId) {
        ProductModel product = productService.getOrThrow(productRepository.find(productId));
        product.delete();
        productRepository.save(product);
    }
}
