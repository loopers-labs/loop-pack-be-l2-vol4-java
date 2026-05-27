package com.loopers.application.catalog.product;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public ProductResult create(ProductCommand.Create command) {
        Brand brand = getActiveBrand(command.brandId());
        Product product = new Product(
            command.brandId(),
            command.name(),
            command.description(),
            command.price(),
            command.stockQuantity(),
            command.status()
        );

        return ProductResult.from(productRepository.save(product), brand);
    }

    @Transactional
    public ProductResult update(Long productId, ProductCommand.Update command) {
        Product product = getProduct(productId);
        Brand brand = getActiveBrand(product.getBrandId());

        product.update(
            command.name(),
            command.description(),
            command.price(),
            command.stockQuantity(),
            command.status()
        );

        return ProductResult.from(productRepository.save(product), brand);
    }

    @Transactional
    public void stopSelling(Long productId) {
        Product product = getProduct(productId);
        product.stopSelling();
        productRepository.save(product);
    }

    private Product getProduct(Long productId) {
        return productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
    }

    private Brand getActiveBrand(Long brandId) {
        Brand brand = brandRepository.find(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + brandId + "] 브랜드를 찾을 수 없습니다."));
        if (!brand.isActive()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 브랜드에는 상품을 등록할 수 없습니다.");
        }

        return brand;
    }
}
