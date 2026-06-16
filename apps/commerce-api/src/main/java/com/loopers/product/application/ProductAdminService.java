package com.loopers.product.application;

import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.brand.domain.BrandErrorCode;
import com.loopers.product.domain.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductAdminService {

    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public ProductResult.AdminDetail create(ProductCommand.Create command) {
        if (!brandRepository.existsById(command.brandId())) {
            throw new CoreException(ErrorType.NOT_FOUND, BrandErrorCode.BRAND_NOT_FOUND);
        }
        Product product = Product.create(
                command.brandId(), command.name(), command.description(), command.price(), command.thumbnailUrl()
        );
        Product saved = productRepository.save(product);
        productStockRepository.save(ProductStock.create(saved.getId(), command.initialStockQuantity()));
        return ProductResult.AdminDetail.from(saved, command.initialStockQuantity());
    }

    @Transactional
    public ProductResult.AdminDetail update(ProductCommand.Update command) {
        Product product = get(command.productId());
        product.update(command.name(), command.description(), command.price(), command.thumbnailUrl());
        int stockQuantity = getStock(command.productId()).getQuantity();
        return ProductResult.AdminDetail.from(product, stockQuantity);
    }

    @Transactional
    public void delete(Long productId) {
        Product product = get(productId);
        ProductStock stock = getStock(productId);
        product.delete();
        stock.delete();
    }

    @Transactional
    public void suspend(Long productId) {
        get(productId).suspend();
    }

    @Transactional
    public void resume(Long productId) {
        get(productId).resume();
    }

    @Transactional(readOnly = true)
    public ProductResult.AdminDetail getProduct(Long productId) {
        Product product = get(productId);
        int stockQuantity = getStock(productId).getQuantity();
        return ProductResult.AdminDetail.from(product, stockQuantity);
    }

    @Transactional(readOnly = true)
    public List<ProductResult.AdminDetail> getProducts() {
        List<Product> products = productRepository.findAllOrderByLatest();
        Map<Long, Integer> stockByProductId = productStockRepository
                .findAllByProductIdIn(products.stream().map(Product::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProductStock::getProductId, ProductStock::getQuantity));
        return products.stream()
                .map(product -> ProductResult.AdminDetail.from(product, stockByProductId.getOrDefault(product.getId(), 0)))
                .toList();
    }

    private Product get(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private ProductStock getStock(Long productId) {
        return productStockRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ProductErrorCode.STOCK_NOT_FOUND));
    }
}
