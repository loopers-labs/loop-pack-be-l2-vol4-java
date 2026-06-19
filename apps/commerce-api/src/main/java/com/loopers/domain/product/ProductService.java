package com.loopers.domain.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductReader productReader;
    private final ProductWriter productWriter;

    public Product createProduct(Long brandId, String name, String description, Long price, Integer stock) {
        return productWriter.createProduct(brandId, name, description, price, stock);
    }

    public Product getProduct(Long id) {
        return productReader.getProduct(id);
    }

    public List<Product> findProductsByIds(List<Long> ids) {
        return productReader.findProductsByIds(ids);
    }

    public List<Product> findProductsByIdsForUpdate(List<Long> ids) {
        return productReader.findProductsByIdsForUpdate(ids);
    }

    public List<Product> getAllProducts(Long brandId, String sort, Integer page, Integer size) {
        return productReader.getAllProducts(brandId, sort, page, size);
    }

    public Product updateProduct(Long id, String name, String description, Long price, Integer stock) {
        return productWriter.updateProduct(id, name, description, price, stock);
    }

    public void saveProducts(List<Product> products) {
        productWriter.saveProducts(products);
    }

    public void updateLikeCountSnapshot(Long productId, Integer likeCount) {
        productWriter.updateLikeCountSnapshot(productId, likeCount);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productWriter.deleteProduct(id);
    }
}
