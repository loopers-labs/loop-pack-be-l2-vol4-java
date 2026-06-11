package com.loopers.product.application;

import com.loopers.brand.domain.BrandRepository;
import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
    private final ProductReader productReader;

    @Transactional
    public ProductResult.AdminDetail create(ProductCommand.Create command) {
        if (!brandRepository.existsById(command.brandId())) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
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
        Product product = productReader.get(command.productId());
        product.update(command.name(), command.description(), command.price(), command.thumbnailUrl());
        int stockQuantity = productReader.getStock(command.productId()).getQuantity();
        return ProductResult.AdminDetail.from(product, stockQuantity);
    }

    @Transactional
    public void delete(Long productId) {
        Product product = productReader.get(productId);
        ProductStock stock = productReader.getStock(productId);
        product.delete();
        stock.delete();
    }

    @Transactional
    public void suspend(Long productId) {
        productReader.get(productId).suspend();
    }

    @Transactional
    public void resume(Long productId) {
        productReader.get(productId).resume();
    }

    @Transactional(readOnly = true)
    public ProductResult.AdminDetail get(Long productId) {
        Product product = productReader.get(productId);
        int stockQuantity = productReader.getStock(productId).getQuantity();
        return ProductResult.AdminDetail.from(product, stockQuantity);
    }

    @Transactional(readOnly = true)
    public List<ProductResult.AdminDetail> getAll() {
        List<Product> products = productRepository.findAllOrderByLatest();
        Map<Long, Integer> stockByProductId = productStockRepository
                .findAllByProductIdIn(products.stream().map(Product::getId).toList())
                .stream()
                .collect(Collectors.toMap(ProductStock::getProductId, ProductStock::getQuantity));
        return products.stream()
                .map(product -> ProductResult.AdminDetail.from(product, stockByProductId.getOrDefault(product.getId(), 0)))
                .toList();
    }
}
