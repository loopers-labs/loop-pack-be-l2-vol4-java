package com.loopers.domain.product.service;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ProductDomainService {

    private final ProductRepository productRepository;

    public Product createProduct(Long brandId, String name, String description, Long price){
        Product product = Product.create(brandId, name, description, price);
        return productRepository.save(product);
    }
}
