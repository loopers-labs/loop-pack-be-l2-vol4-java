package com.loopers.application.brand;

import com.loopers.application.product.ProductCacheEvictEvent;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final StockService stockService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void deleteBrand(Long id) {
        brandService.delete(id);
        List<Long> productIds = productService.findAllByBrandId(id)
            .stream().map(p -> p.getId()).toList();
        productService.softDeleteAllByBrandId(id);
        stockService.softDeleteAllByProductIds(productIds);
        if (!productIds.isEmpty()) {
            eventPublisher.publishEvent(new ProductCacheEvictEvent(productIds));
        }
    }

}
