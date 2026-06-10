package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    @Transactional
    public ProductInfo createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        ProductModel product = productRepository.save(new ProductModel(brandId, name, description, price, stock));
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long id) {
        return ProductInfo.from(loadProduct(id));
    }

    @Transactional(readOnly = true)
    public ProductDetailInfo getProductDetail(Long id) {
        ProductModel product = loadProduct(id);
        BrandModel brand = brandRepository.find(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        return ProductDetailInfo.from(product, brand);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(Long brandId, String sort, int page, int size) {
        if (page < 0 || size <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이지 정보가 올바르지 않습니다.");
        }
        return productRepository.findAll(brandId, ProductSortType.from(sort), page, size).stream()
            .map(ProductInfo::from)
            .toList();
    }

    @Transactional
    public ProductInfo updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = loadProduct(id);
        product.update(name, description, price, stock);
        return ProductInfo.from(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = loadProduct(id);
        product.delete(); // soft delete
        productRepository.save(product);
    }

    private ProductModel loadProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }
}
