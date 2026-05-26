package com.loopers.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductFacade {

    private final BrandRepository brandRepository;

    private final ProductRepository productRepository;

    public ProductCreateInfo createProduct(Long brandId, String name, String description, Integer price, Integer stock) {
        if (!brandRepository.existsActiveById(brandId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드가 존재하지 않습니다.");
        }

        ProductModel newProduct = ProductModel.builder()
            .brandId(brandId)
            .rawName(name)
            .rawDescription(description)
            .rawPrice(price)
            .rawStock(stock)
            .build();

        return ProductCreateInfo.from(productRepository.save(newProduct));
    }

    public ProductUpdateInfo updateProduct(Long productId, String name, String description, Integer price, Integer stock) {
        ProductModel product = productRepository.getActiveById(productId);

        product.update(name, description, price, stock);

        return ProductUpdateInfo.from(product);
    }

    public void deleteProduct(Long productId) {
        productRepository.findActiveById(productId)
            .ifPresent(ProductModel::delete);
    }
}
