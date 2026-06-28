package com.loopers.application.product;

import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;
    private final BrandRepository brandRepository;

    public ProductInfo createProduct(String name, String description, Long price, Integer stock, Long brandId) {
        if (!brandRepository.existsById(brandId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.");
        }
        ProductModel product = productService.createProduct(name, description, price, stock, brandId);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        return ProductInfo.from(productService.getProduct(id));
    }

    public Page<ProductInfo> getProducts(Long brandId, ProductSortType sort, Pageable pageable) {
        return productService.getProducts(brandId, sort, pageable).map(ProductInfo::from);
    }

    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        return ProductInfo.from(productService.updateProduct(id, name, description, price, stock));
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
