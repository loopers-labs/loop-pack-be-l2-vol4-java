package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductWriter {

    private final ProductRepository productRepository;
    private final ProductReader productReader;
    private final ProductBrandProcessor productBrandProcessor;

    public ProductDetail createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        BrandModel brand = productReader.getBrand(brandId);
        ProductModel product = productRepository.save(new ProductModel(brandId, name, description, price, stock));
        return productBrandProcessor.getProductDetail(product, brand);
    }

    public ProductDetail updateProduct(Long id, String name, String description, Long price, Integer stock) {
        ProductModel product = productReader.getProductModel(id);
        product.update(name, description, price, stock);
        ProductModel savedProduct = productRepository.save(product);
        BrandModel brand = productReader.getBrand(savedProduct.getBrandId());
        return productBrandProcessor.getProductDetail(savedProduct, brand);
    }

    public void deleteProduct(Long id) {
        ProductModel product = productReader.getProductModel(id);
        product.delete();
        productRepository.save(product);
    }
}
