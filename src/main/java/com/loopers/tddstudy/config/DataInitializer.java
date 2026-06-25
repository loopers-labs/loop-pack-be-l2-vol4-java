package com.loopers.tddstudy.config;

import com.loopers.tddstudy.domain.brand.Brand;
import com.loopers.tddstudy.domain.brand.BrandRepository;
import com.loopers.tddstudy.domain.product.Product;
import com.loopers.tddstudy.domain.product.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;


@Component
public class DataInitializer implements ApplicationRunner {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public DataInitializer(BrandRepository brandRepository, ProductRepository productRepository) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Brand brand = new Brand("테스트브랜드", "설명", 1L);
        brand.publish();
        brandRepository.save(brand);


        List<Product> products = new ArrayList<>();
        for (int i = 1; i <= 100000; i++) {
            Product product = new Product("상품" + i, i * 100, 100, brand.getId());
            product.publish();
            products.add(product);
        }
        productRepository.saveAll(products);
    }


}
