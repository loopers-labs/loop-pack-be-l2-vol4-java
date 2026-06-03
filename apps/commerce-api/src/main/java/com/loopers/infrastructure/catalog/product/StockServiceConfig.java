package com.loopers.infrastructure.catalog.product;

import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.StockService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StockServiceConfig {

    @Bean
    public StockService stockService(ProductRepository productRepository) {
        return new StockService(productRepository);
    }
}
