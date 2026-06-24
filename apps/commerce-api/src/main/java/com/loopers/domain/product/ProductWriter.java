package com.loopers.domain.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductWriter {

    private final ProductRepository productRepository;
    private final ProductReader productReader;

    public Product createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        return productRepository.save(new Product(brandId, name, description, price, stock));
    }

    public Product updateProduct(Long id, String name, String description, Long price, Integer stock) {
        Product product = productReader.getProductById(id);
        product.update(name, description, price, stock);
        return productRepository.save(product);
    }

    public void saveProducts(List<Product> products) {
        products.forEach(productRepository::save);
    }

    public void updateLikeCountSnapshot(Long productId, Integer likeCount) {
        productRepository.updateLikeCount(productId, likeCount);
    }

    public void deleteProduct(Long id) {
        Product product = productReader.getProductById(id);
        product.delete();
        productRepository.save(product);
    }
}
