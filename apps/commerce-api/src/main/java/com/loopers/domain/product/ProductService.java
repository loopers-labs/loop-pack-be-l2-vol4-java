package com.loopers.domain.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    public List<Product> getAllProducts(Long brandId, String sort, Integer page, Integer size) {
        return productReader.getAllProducts(brandId, sort, page, size);
    }

    public List<Product> getProductsByIds(List<Long> ids) {
        return productReader.getProductsByIds(ids);
    }

    public Product updateProduct(Long id, String name, String description, Long price, Integer stock) {
        return productWriter.updateProduct(id, name, description, price, stock);
    }

    public void deleteProduct(Long id) {
        productWriter.deleteProduct(id);
    }
}
